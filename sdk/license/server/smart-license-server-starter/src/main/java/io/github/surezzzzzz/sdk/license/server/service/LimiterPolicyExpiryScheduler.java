package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.constant.LimiterPolicyBindingState;
import io.github.surezzzzzz.sdk.license.server.entity.LimiterPolicyBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.LimiterPolicyBindingRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 到期授权收敛任务：服务截止后先停用客户限频，再把授权置为 SUSPENDED。
 *
 * <p>远程履约失败时保留 ENABLED，下一轮继续尝试，不把未知结果伪装成已停用。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleLicenseServerComponent
public class LimiterPolicyExpiryScheduler {

    private static final int BATCH_SIZE = 64;
    private final GrantRepository grants;
    private final PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private LimiterPolicyProjectionService projection;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private LimiterPolicyBindingRepository bindings;

    @Autowired(required = false)
    private LicenseRevocationGuard revocationGuard;

    @Autowired
    public LimiterPolicyExpiryScheduler(GrantRepository grants,
                                        PlatformTransactionManager transactionManager) {
        this.grants = grants;
        this.transactionManager = transactionManager;
    }

    public LimiterPolicyExpiryScheduler(GrantRepository grants) {
        this(grants, null);
    }

    @Scheduled(fixedDelayString =
            "${io.github.surezzzzzz.sdk.license.server.limiter-expiry-scan-delay-millis:60000}")
    public void reconcile() {
        long now = Instant.now().getEpochSecond();
        List<GrantRevisionEntity> candidates = transaction(() -> grants.findByStateAndServiceUntilLessThanEqual(
                "ENABLED", now, PageRequest.of(0, BATCH_SIZE, Sort.by("grantId"))).getContent());
        if (candidates == null) {
            candidates = java.util.Collections.emptyList();
        }
        for (GrantRevisionEntity candidate : candidates) {
            processExpired(candidate, now);
        }
        reconcileBindings();
    }

    /** 每份授权单独提交；一条远端未知不会把同批其他授权标成 rollback-only。 */
    private void processExpired(GrantRevisionEntity candidate, long now) {
        try {
            Object event = transaction(() -> {
                GrantRevisionEntity grant = candidate;
                if (transactionManager != null) {
                    grant = grants.findWithLockByGrantId(candidate.getGrantId()).orElse(null);
                }
                if (grant == null || !"ENABLED".equals(grant.getState())
                        || grant.getServiceUntil() == null || grant.getServiceUntil() > now) {
                    return null;
                }
                if (hasPolicies(grant)) {
                    if (projection == null) {
                        log.warn("授权到期但限频履约端口未装配：grantId={}", grant.getGrantId());
                        throw LicenseHttpException.dependencyUnavailable("客户限频履约台账未装配");
                    }
                    projection.disable(grant);
                }
                grant.setState("SUSPENDED");
                grant.setGrantStateVersion(grant.getGrantStateVersion() + 1L);
                grants.save(grant);
                return new io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.GrantStateChanged(
                        "system:license-expiry", "license-expiry-" + UUID.randomUUID(),
                        grant.getGrantId(), "ENABLED", "SUSPENDED");
            });
            if (eventPublisher != null && event != null) {
                eventPublisher.publishEvent(event);
            }
        } catch (RuntimeException failure) {
            log.warn("授权到期收敛未确认：grantId={}，异常类型={}", candidate.getGrantId(),
                    failure.getClass().getSimpleName());
        }
    }

    /** 远端调用成功后本地提交失败也不能留下长期孤儿；按本地授权状态再次收敛。 */
    private void reconcileBindings() {
        if (bindings == null || projection == null) {
            return;
        }
        List<LimiterPolicyBindingEntity> candidates = transaction(() -> bindings.findByStateIn(Arrays.asList(
                LimiterPolicyBindingState.PENDING_SYNC, LimiterPolicyBindingState.SYNCED,
                LimiterPolicyBindingState.DISABLE_PENDING, LimiterPolicyBindingState.DISABLED,
                LimiterPolicyBindingState.FAILED,
                LimiterPolicyBindingState.UNCERTAIN)));
        Set<String> processed = new HashSet<String>();
        if (candidates == null) {
            return;
        }
        for (LimiterPolicyBindingEntity binding : candidates) {
            if (!processed.add(binding.getGrantId())) {
                continue;
            }
            reconcileBindingGrant(binding.getGrantId());
        }
    }

    /** 补偿按授权隔离事务，且每次从数据库重新读取授权状态。 */
    private void reconcileBindingGrant(String grantId) {
        try {
            transaction(() -> {
                GrantRevisionEntity grant;
                if (transactionManager != null) {
                    grant = grants.findWithLockByGrantId(grantId).orElse(null);
                } else {
                    grant = grants.findById(grantId).orElse(null);
                }
                if (grant == null) {
                    return null;
                }
                boolean revoked = revocationGuard != null
                        && revocationGuard.isRevoked(
                        io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind.GRANT,
                        grant.getGrantId());
                boolean usable = "ENABLED".equals(grant.getState()) && !revoked
                        && (grant.getServiceUntil() == null
                        || grant.getServiceUntil() > Instant.now().getEpochSecond());
                if (usable) {
                    projection.enable(grant);
                } else {
                    projection.disable(grant);
                }
                return null;
            });
        } catch (RuntimeException failure) {
            log.warn("限频绑定补偿未完成：grantId={}，异常类型={}", grantId,
                    failure.getClass().getSimpleName());
        }
    }

    private <T> T transaction(Supplier<T> action) {
        if (transactionManager == null) {
            return action.get();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private static boolean hasPolicies(GrantRevisionEntity grant) {
        return grant.getLimiterPoliciesJson() != null
                && !grant.getLimiterPoliciesJson().trim().isEmpty()
                && !"[]".equals(grant.getLimiterPoliciesJson().trim());
    }
}
