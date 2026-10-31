package io.github.surezzzzzz.sdk.license.server.integration;

import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.FulfillmentConstant;
import io.github.surezzzzzz.sdk.license.server.entity.FulfillmentGrantEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.FulfillmentGrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 履约授权建立与恢复：台账和草稿同事务提交，唯一冲突后从新事务回读赢家。
 *
 * <p>事务只覆盖 License 自有持久化；CRM 认领和回写必须由协调器在事务外调用。
 * 唯一异常在事务已回滚后处理，不在 rollback-only 事务内继续保存或回读。
 * 兼容唯一存量授权时补关联；有歧义或断裂关联时停止，不随机选择或重新建权。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleLicenseServerComponent
public class FulfillmentGrantCreationService {

    /** 返回已提交的授权事实，区分本轮新建与复用。 */
    @Getter
    @RequiredArgsConstructor
    public static final class CreationResult {
        private final GrantRevisionEntity grant;
        private final boolean created;
    }

    private final GrantRepository grants;
    private final FulfillmentGrantRepository associations;
    private final TransactionTemplate transactions;

    /** 独立本地事务确保返回时已完成提交，外部 HTTP 不被宿主外围事务裹入。 */
    public FulfillmentGrantCreationService(GrantRepository grants, FulfillmentGrantRepository associations,
                                          PlatformTransactionManager transactionManager) {
        this.grants = grants;
        this.associations = associations;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 回读已提交关联，唯一存量授权可在同一事务内补齐台账。 */
    public Optional<GrantRevisionEntity> findExisting(String fulfillmentId) {
        requireReference(fulfillmentId);
        try {
            return transactions.execute(status -> findOrAdoptExisting(fulfillmentId));
        } catch (DataIntegrityViolationException conflict) {
            return Optional.of(recoverWinner(fulfillmentId, conflict));
        }
    }

    /** 建立草稿或复用并发赢家，绝不自动启用商业授权。 */
    public CreationResult createOrRecover(String fulfillmentId, String customerId, String productId) {
        requireReference(fulfillmentId);
        requireReference(customerId);
        requireReference(productId);
        try {
            return transactions.execute(status -> {
                Optional<GrantRevisionEntity> existing = findOrAdoptExisting(fulfillmentId);
                if (existing.isPresent()) {
                    return new CreationResult(existing.get(), false);
                }
                GrantRevisionEntity grant = grants.save(new GrantRevisionEntity(
                        FulfillmentConstant.GRANT_ID_PREFIX + UUID.randomUUID(), customerId, productId,
                        SubjectKind.PERSON.getCode(), EligibilityPolicy.Kind.CUSTOMER_MEMBERS.getCode(),
                        null, EligibilityPolicy.Mode.ONLINE.getCode(), fulfillmentId));
                associations.saveAndFlush(new FulfillmentGrantEntity(fulfillmentId, grant.getGrantId()));
                return new CreationResult(grant, true);
            });
        } catch (DataIntegrityViolationException conflict) {
            GrantRevisionEntity winner = recoverWinner(fulfillmentId, conflict);
            log.debug("履约并发建立已恢复：fulfillmentId={}，grantId={}", fulfillmentId, winner.getGrantId());
            return new CreationResult(winner, false);
        }
    }

    private Optional<GrantRevisionEntity> findOrAdoptExisting(String fulfillmentId) {
        Optional<FulfillmentGrantEntity> association = associations.findById(fulfillmentId);
        if (association.isPresent()) {
            return Optional.of(loadAssociatedGrant(fulfillmentId, association.get()));
        }
        List<GrantRevisionEntity> legacy = grants.findByBusinessOrderRef(fulfillmentId);
        if (legacy.size() > 1) {
            throw new IllegalStateException(FulfillmentConstant.AMBIGUOUS_GRANT);
        }
        if (legacy.isEmpty()) {
            return Optional.empty();
        }
        GrantRevisionEntity grant = legacy.get(0);
        associations.saveAndFlush(new FulfillmentGrantEntity(fulfillmentId, grant.getGrantId()));
        log.debug("履约存量关联已建立：fulfillmentId={}，grantId={}", fulfillmentId, grant.getGrantId());
        return Optional.of(grant);
    }

    private GrantRevisionEntity recoverWinner(String fulfillmentId, DataIntegrityViolationException conflict) {
        // 唯一冲突事务已退出；新事务不能沿用输家读取快照或其未提交草稿。
        return transactions.execute(status -> {
            Optional<FulfillmentGrantEntity> winner = associations.findById(fulfillmentId);
            if (!winner.isPresent()) {
                throw conflict;
            }
            return loadAssociatedGrant(fulfillmentId, winner.get());
        });
    }

    private GrantRevisionEntity loadAssociatedGrant(String fulfillmentId, FulfillmentGrantEntity association) {
        GrantRevisionEntity grant = grants.findById(association.getGrantId()).orElseThrow(
                () -> new IllegalStateException(FulfillmentConstant.BROKEN_ASSOCIATION));
        if (!fulfillmentId.equals(grant.getBusinessOrderRef())) {
            throw new IllegalStateException(FulfillmentConstant.BROKEN_ASSOCIATION);
        }
        return grant;
    }

    private void requireReference(String reference) {
        if (!StringUtils.hasText(reference) || reference.length() > FulfillmentConstant.REFERENCE_MAX_LENGTH) {
            throw new IllegalArgumentException(FulfillmentConstant.INVALID_REFERENCE);
        }
    }
}
