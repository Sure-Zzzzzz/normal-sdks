package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyRevisionEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicySnapshotView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementMapper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 默认服务级限流策略快照服务
 *
 * @author surezzzzzz
 */
public class DefaultSmartRedisLimiterPolicySnapshotService
        implements SmartRedisLimiterPolicySnapshotService {

    private final SmartRedisLimiterPolicyRepository repository;

    /**
     * 构造快照服务
     *
     * @param repository 策略 Repository
     */
    public DefaultSmartRedisLimiterPolicySnapshotService(
            SmartRedisLimiterPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SmartRedisLimiterPolicySnapshotView getSnapshot(String serviceCode) {
        String normalizedServiceCode = SmartRedisLimiterPolicyValidationHelper
                .normalizeServiceCode(serviceCode);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity =
                repository.findRevision(normalizedServiceCode);
        long revision = revisionEntity == null ? 0L : revisionEntity.getRevision();
        Instant publishedAt = revisionEntity == null ? Instant.EPOCH : revisionEntity.getPublishedAt();

        List<SmartRedisLimiterPolicy> policies = new ArrayList<>();
        for (SmartRedisLimiterPolicyEntity entity
                : repository.findEnabledByServiceCode(normalizedServiceCode)) {
            policies.add(SmartRedisLimiterManagementMapper.toCorePolicy(entity));
        }
        policies.sort(Comparator
                .comparing((SmartRedisLimiterPolicy policy) -> policy.getKey().getResourceCode())
                .thenComparing(policy -> policy.getKey().getSubject()));
        SmartRedisLimiterPolicySnapshot snapshot = new SmartRedisLimiterPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                normalizedServiceCode,
                revision,
                publishedAt,
                policies);
        return new SmartRedisLimiterPolicySnapshotView(
                snapshot,
                SmartRedisLimiterEtagHelper.build(normalizedServiceCode, revision));
    }
}
