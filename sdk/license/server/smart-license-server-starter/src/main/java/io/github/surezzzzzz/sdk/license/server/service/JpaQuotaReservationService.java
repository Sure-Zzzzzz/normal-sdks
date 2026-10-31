package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.QuotaConstant;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaReservationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.model.QuotaReservationCommand;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在线额度台账：真实资格复核、余额、主体幂等及预占状态在独立事务内提交。
 *
 * <p>统一先锁grant×metric余额再锁预占行。预占到期由下一次操作懒回收；累计消费不可回补。
 * 唯一键竞争失败必须完成回滚后再开事务读赢家，避免宿主事务rollback-only污染。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleLicenseServerComponent
public class JpaQuotaReservationService {

    private final QuotaReservationRepository reservations;
    private final GrantRepository grants;
    private final JpaQuotaLedgerService ledger;
    private final QuotaPolicyService policies;
    private final PlatformTransactionManager transactionManager;
    private final LongSupplier clock;

    /** 生产装配必须有业务政策和事务管理器；不提供成功替身。 */
    @Autowired
    public JpaQuotaReservationService(QuotaReservationRepository reservations, GrantRepository grants,
                                      JpaQuotaLedgerService ledger, QuotaPolicyService policies,
                                      PlatformTransactionManager transactionManager) {
        this(reservations, grants, ledger, policies, transactionManager, () -> Instant.now().getEpochSecond());
    }

    /** 隔离测试以确定时刻核到期边界；生产使用上方完整构造。 */
    public JpaQuotaReservationService(QuotaReservationRepository reservations, GrantRepository grants,
                                      JpaQuotaLedgerService ledger, QuotaPolicyService policies,
                                      PlatformTransactionManager transactionManager, LongSupplier clock) {
        this.reservations = reservations;
        this.grants = Objects.requireNonNull(grants);
        this.ledger = ledger;
        this.policies = Objects.requireNonNull(policies);
        this.transactionManager = transactionManager;
        this.clock = Objects.requireNonNull(clock);
    }

    /** 同一真实认证主体下的业务编号重放返回首次记录，跨主体编号互不冲突。 */
    public QuotaReservationEntity reserve(QuotaReservationCommand command) {
        String grantId = command.getGrantId();
        String metric = command.getMetric();
        long quantity = command.getQuantity();
        String kind = command.getKind();
        String businessOperationId = command.getBusinessOperationId();
        String principalId = command.getPrincipalId();
        String sourceId = command.getAuthenticationSourceId();
        String subjectType = command.getSubjectType();
        String assignmentId = command.getAssignmentId();
        String activationId = command.getActivationId();
        if (blank(command.getProductId()) || blank(command.getSubjectKind()) || blank(command.getAction())
                || command.getAction().length() > QuotaConstant.ID_LENGTH || command.getExpectedGrantRevision() < 1L) {
            throw new IllegalArgumentException(QuotaConstant.INVALID_INPUT);
        }
        validateInput(grantId, metric, quantity, kind, businessOperationId, principalId, sourceId,
                assignmentId, activationId);
        // 业务号候选键保持稳定；数量、动作、种类和授权修订由回放核对拒绝冲突，不能生成第二笔扣减。
        String operationKey = operationKey(grantId, metric, businessOperationId, principalId, sourceId,
                activationId == null ? "" : activationId);
        try {
            return transaction(false, () -> reserveInTransaction(grantId, metric, quantity, kind,
                    businessOperationId, principalId, sourceId, subjectType, assignmentId, activationId, operationKey,
                    command));
        } catch (DataIntegrityViolationException duplicate) {
            // 失败事务先回滚余额；回读也REQUIRES_NEW，不能复用宿主已标记rollback-only的事务。
            return transaction(true, () -> {
                // 竞争事务不能绕过当前授权/产品/主体种类/修订准入；只读复核不改变首次回执语义。
                policies.require(command.getProductId(), command.getSubjectKind(), command.getAction(),
                        command.getExpectedGrantRevision(), grantId, metric, kind, principalId, sourceId,
                        subjectType, assignmentId, activationId, clock.getAsLong());
                QuotaReservationEntity winner = reservations.findByOperationKey(operationKey)
                        .orElseThrow(() -> duplicate);
                verifyReplay(winner, command);
                log.debug("额度操作唯一键竞争已回读：reservationId={}", winner.getReservationId());
                return winner;
            });
        }
    }

    private QuotaReservationEntity reserveInTransaction(String grantId, String metric, long quantity, String kind,
                                                        String businessOperationId, String principalId,
                                                        String sourceId, String subjectType, String assignmentId,
                                                        String activationId, String operationKey,
                                                        QuotaReservationCommand command) {
        long now = clock.getAsLong();
        QuotaPolicyService.Policy policy = policies.require(command.getProductId(), command.getSubjectKind(),
                command.getAction(), command.getExpectedGrantRevision(), grantId, metric, kind, principalId, sourceId,
                subjectType, assignmentId, activationId, now);
        QuotaBalanceEntity balance = ledger.lock(grantId, metric);
        reclaim(balance, now);
        QuotaReservationEntity existing = reservations.findByOperationKey(operationKey).orElse(null);
        if (existing != null) {
            verifyReplay(existing, command);
            return existing;
        }
        if (balance.getQuotaLimit() != policy.getLimit()) {
            throw new QuotaUnavailableException(QuotaConstant.INVALID_POLICY);
        }
        long validUntil = policy.getValidUntil();
        if (policy.getPeriodSeconds() > 0L) {
            balance.initializeWindowAnchor(policy.getInitialAnchorAt());
            balance.initializeWindowPeriod(policy.getPeriodSeconds());
            long anchor = balance.getWindowAnchorAt();
            if (now < anchor) {
                throw new QuotaUnavailableException(QuotaConstant.UNUSABLE);
            }
            long period = policy.getPeriodSeconds();
            long position = Math.floorMod(Math.floorMod(now, period) - Math.floorMod(anchor, period), period);
            long until = future(now, period - position);
            if (balance.getWindowUntil() <= now) {
                balance.advanceWindow(until);
            }
            validUntil = Math.min(validUntil, until);
        }
        if (quantity > balance.getRemaining()) {
            throw new QuotaUnavailableException(QuotaConstant.INSUFFICIENT);
        }
        balance.deduct(quantity);
        ledger.save(balance);
        QuotaReservationEntity created = new QuotaReservationEntity(
                QuotaConstant.RESERVATION_PREFIX + UUID.randomUUID(), grantId, metric, quantity, kind,
                command.getAction(), command.getExpectedGrantRevision(),
                businessOperationId, operationKey, balance.getFenceSequence(), validUntil, principalId,
                sourceId, assignmentId, activationId);
        QuotaReservationEntity saved = reservations.saveAndFlush(created);
        log.debug("额度操作已落账：reservationId={}，kind={}，fence={}",
                saved.getReservationId(), saved.getKind(), saved.getFence());
        return saved;
    }

    /** 释放只核认证归属与DATA；授权暂停/到期后仍允许本人归还预占。 */
    public QuotaReservationEntity release(String reservationId, String principalId, String sourceId) {
        // 只在写事务外读取不可变归属和路由。锁前把旧实体载入写事务的持久上下文，
        // 会在等待余额锁后与另一实例已提交的新行版本冲突；写事务内必须先锁余额再首读操作行。
        QuotaReservationEntity observed = owned(reservationId, principalId, sourceId);
        requireConcurrent(observed);
        return transaction(false, () -> {
            QuotaBalanceEntity balance = ledger.lock(observed.getGrantId(), observed.getMetric());
            reclaim(balance, clock.getAsLong());
            QuotaReservationEntity entity = lockedOwned(reservationId, principalId, sourceId);
            if (!QuotaConstant.ACTIVE.equals(entity.getState())) {
                return entity;
            }
            balance.release(entity.getQuantity());
            ledger.save(balance);
            entity.release();
            log.debug("额度预占已释放：reservationId={}，fence={}", reservationId, entity.getFence());
            return reservations.saveAndFlush(entity);
        });
    }

    /** 续期复查资格、政策及预期围栏；新截止不超过模型上限和商业截止。 */
    public QuotaReservationEntity renew(String reservationId, long expectedFence, String principalId,
                                        String sourceId, String subjectType) {
        if (expectedFence < 0L) {
            throw new IllegalArgumentException(QuotaConstant.INVALID_INPUT);
        }
        QuotaReservationEntity observed = owned(reservationId, principalId, sourceId);
        requireConcurrent(observed);
        return transaction(false, () -> {
            long now = clock.getAsLong();
            // 续期请求只有围栏和认证归属；产品/对象类型必须从 License 授权台账回读，不能由调用方补送。
            GrantRevisionEntity grant = grants.findById(observed.getGrantId())
                    .orElseThrow(LicenseHttpException::notFound);
            QuotaPolicyService.Policy policy = policies.require(grant.getProductId(), grant.getSubjectKind(),
                    observed.getAction(), observed.getGrantRevision(),
                    observed.getGrantId(), observed.getMetric(),
                    observed.getKind(), principalId, sourceId, subjectType, observed.getAssignmentId(),
                    observed.getActivationId(), now);
            QuotaBalanceEntity balance = ledger.lock(observed.getGrantId(), observed.getMetric());
            reclaim(balance, now);
            QuotaReservationEntity entity = lockedOwned(reservationId, principalId, sourceId);
            if (!QuotaConstant.ACTIVE.equals(entity.getState()) || entity.getValidUntil() <= now
                    || entity.getFence() != expectedFence) {
                throw new IllegalStateException(QuotaConstant.ENDED);
            }
            if (policy.getValidUntil() <= entity.getValidUntil()) {
                return entity;
            }
            entity.renew(balance.nextFence(), policy.getValidUntil());
            ledger.save(balance);
            log.debug("额度预占已续期：reservationId={}，fence={}", reservationId, entity.getFence());
            return reservations.saveAndFlush(entity);
        });
    }

    /** 只读操作事实；Controller必须先对它的grantId执行DATA校验。 */
    public Optional<QuotaReservationEntity> find(String reservationId) {
        return blank(reservationId) ? Optional.empty() : reservations.findById(reservationId);
    }

    /** 受控读取额度操作元数据；控制器先将 DataPlan 投影为授权主键范围。 */
    public Page<QuotaReservationEntity> list(String grantId, String activationId, String metric, String kind,
                                             String state, Set<String> grantIds, Pageable pageable) {
        return reservations.findVisiblePage(grantId, activationId, metric, kind, state, grantIds, pageable);
    }

    /** 运行端详情读取同时核对当前认证主体和认证来源，不能把 DATA 当作主体证明。 */
    public QuotaReservationEntity findOwned(String reservationId, String principalId, String sourceId) {
        return owned(reservationId, principalId, sourceId);
    }

    private QuotaReservationEntity owned(String id, String principalId, String sourceId) {
        QuotaReservationEntity value = find(id)
                .orElseThrow(() -> new IllegalArgumentException(QuotaConstant.MISSING));
        requireOwner(value, principalId, sourceId);
        return value;
    }

    private QuotaReservationEntity lockedOwned(String id, String principalId, String sourceId) {
        QuotaReservationEntity value = reservations.findWithLockByReservationId(id)
                .orElseThrow(() -> new IllegalArgumentException(QuotaConstant.MISSING));
        requireOwner(value, principalId, sourceId);
        return value;
    }

    private void reclaim(QuotaBalanceEntity balance, long now) {
        for (QuotaReservationEntity expired : reservations
                .findByGrantIdAndMetricAndKindAndStateAndValidUntilLessThanEqual(balance.getGrantId(),
                        balance.getMetric(), QuotaConstant.CONCURRENT, QuotaConstant.ACTIVE, now)) {
            balance.release(expired.getQuantity());
            expired.expire();
            reservations.saveAndFlush(expired);
            log.debug("额度预占到期已回收：reservationId={}", expired.getReservationId());
        }
        ledger.save(balance);
    }

    private <T> T transaction(boolean readOnly, Supplier<T> action) {
        if (transactionManager == null) {
            return action.get();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(readOnly);
        return template.execute(status -> action.get());
    }

    private static void requireOwner(QuotaReservationEntity value, String principalId, String sourceId) {
        if (blank(principalId) || blank(sourceId) || !principalId.equals(value.getRequesterSubject())
                || !sourceId.equals(value.getAuthenticationSource())) {
            throw new SecurityException(QuotaConstant.NOT_OWNER);
        }
    }

    private static void requireConcurrent(QuotaReservationEntity entity) {
        if (!QuotaConstant.CONCURRENT.equals(entity.getKind())) {
            throw new IllegalStateException(QuotaConstant.CONSUMPTION_FINAL);
        }
    }

    private static void validateInput(String grantId, String metric, long quantity, String kind,
                                      String operationId, String principalId, String sourceId,
                                      String assignmentId, String activationId) {
        if (blank(grantId) || blank(metric) || blank(operationId) || blank(principalId) || blank(sourceId)
                || grantId.length() > QuotaConstant.ID_LENGTH || metric.length() > QuotaConstant.ID_LENGTH
                || operationId.length() > QuotaConstant.OPERATION_LENGTH
                || principalId.length() > QuotaConstant.SUBJECT_LENGTH
                || sourceId.length() > QuotaConstant.SUBJECT_LENGTH || quantity <= 0L
                || assignmentId != null && (blank(assignmentId) || assignmentId.length() > QuotaConstant.ID_LENGTH)
                || activationId != null && (blank(activationId) || activationId.length() > QuotaConstant.ID_LENGTH)
                || !(QuotaConstant.CONCURRENT.equals(kind) || QuotaConstant.CONSUMPTION.equals(kind))) {
            throw new IllegalArgumentException(QuotaConstant.INVALID_INPUT);
        }
    }

    private static void verifyReplay(QuotaReservationEntity existing, QuotaReservationCommand command) {
        requireOwner(existing, command.getPrincipalId(), command.getAuthenticationSourceId());
        // 唯一摘要只决定候选行；仍核全部原始幂等字段和不可变操作内容，不能把摘要当业务证明。
        if (!command.getGrantId().equals(existing.getGrantId()) || !command.getMetric().equals(existing.getMetric())
                || !command.getBusinessOperationId().equals(existing.getBusinessOperationId())
                || existing.getQuantity() != command.getQuantity() || !command.getKind().equals(existing.getKind())
                || existing.getGrantRevision() != command.getExpectedGrantRevision()
                || !Objects.equals(existing.getAssignmentId(), command.getAssignmentId())
                || !Objects.equals(existing.getActivationId(), command.getActivationId())
                || !command.getAction().equals(existing.getAction())) {
            throw new IllegalArgumentException(QuotaConstant.REPLAY_MISMATCH);
        }
    }

    /** 长度分帧摘要没有分隔符歧义；源字段保留在行中供审计与冲突核验。 */
    public static String operationKey(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(Character.forDigit((value & 0xff) >>> 4, 16));
                result.append(Character.forDigit(value & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(QuotaConstant.UNAVAILABLE, impossible);
        }
    }

    private static long future(long now, long seconds) {
        return now > Long.MAX_VALUE - seconds ? Long.MAX_VALUE : now + seconds;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 确定的业务额度不足，与数据库依赖故障分开映射。 */
    public static final class QuotaUnavailableException extends RuntimeException {

        public QuotaUnavailableException(String message) {
            super(message);
        }
    }
}
