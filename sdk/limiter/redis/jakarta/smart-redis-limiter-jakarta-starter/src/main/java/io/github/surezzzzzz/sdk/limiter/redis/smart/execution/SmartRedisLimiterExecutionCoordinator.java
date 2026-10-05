package io.github.surezzzzzz.sdk.limiter.redis.smart.execution;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyGenerator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyResolution;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyResolver;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

/**
 * Aspect 与 Interceptor 共用的请求执行协调器
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterExecutionCoordinator {

    private final SmartRedisLimiterProperties properties;
    private final SmartRedisLimiterAlgorithmFactory algorithmFactory;
    private final SmartRedisLimiterPolicySnapshotStore snapshotStore;
    private final SmartRedisLimiterPolicyResolver policyResolver;

    /**
     * 构造请求执行协调器
     *
     * @param properties       限流器配置
     * @param algorithmFactory 算法工厂
     * @param snapshotStore    可选快照存储
     * @param policyResolver   可选策略解析器
     */
    public SmartRedisLimiterExecutionCoordinator(
            SmartRedisLimiterProperties properties,
            SmartRedisLimiterAlgorithmFactory algorithmFactory,
            ObjectProvider<SmartRedisLimiterPolicySnapshotStore> snapshotStore,
            ObjectProvider<SmartRedisLimiterPolicyResolver> policyResolver) {
        this.properties = properties;
        this.algorithmFactory = algorithmFactory;
        this.snapshotStore = snapshotStore.getIfAvailable();
        this.policyResolver = policyResolver.getIfAvailable();
    }

    /**
     * 解析一次 subject、读取一次快照并执行同一份最终计划
     *
     * @param context      限流上下文
     * @param localLimits  本地完整限额列表
     * @param keyStrategy  Key 生成策略
     * @param algorithm    限流算法
     * @param fallback     降级策略
     * @param resourceCode 稳定资源编码
     * @return 限流执行结果
     */
    public SmartRedisLimiterExecutionOutcome execute(
            SmartRedisLimiterContext context,
            List<SmartRedisLimiterProperties.SmartLimitRule> localLimits,
            String keyStrategy,
            String algorithm,
            String fallback,
            String resourceCode) {
        SmartRedisLimiterAlgorithm algorithmInstance = algorithmFactory.getAlgorithm(algorithm);
        if (resourceCode == null
                || SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE.equals(resourceCode)) {
            SmartRedisLimiterResult result = algorithmInstance.tryAcquireWithResult(
                    context, localLimits, keyStrategy, fallback);
            SmartRedisLimiterExecutionPlan plan = new SmartRedisLimiterExecutionPlan(
                    localLimits, null, result.getRouteKey(), algorithm, fallback,
                    SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE,
                    io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                            .POLICY_SOURCE_LOCAL,
                    null);
            return new SmartRedisLimiterExecutionOutcome(plan, result);
        }

        String policySubject = resolveSubjectOnce(context, keyStrategy);
        SmartRedisLimiterAcceptedPolicySnapshot acceptedSnapshot = snapshotStore == null
                ? null
                : snapshotStore.getCurrent();
        SmartRedisLimiterPolicyResolution resolution = policyResolver == null
                ? new SmartRedisLimiterPolicyResolution(
                localLimits,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                        .POLICY_SOURCE_LOCAL,
                null)
                : policyResolver.resolve(
                acceptedSnapshot,
                properties.getMe(),
                resourceCode,
                policySubject,
                localLimits);
        String baseKey = SmartRedisLimiterKeyHelper.buildPolicyBaseKey(
                properties.getMe(), resourceCode, policySubject);
        SmartRedisLimiterExecutionPlan plan = new SmartRedisLimiterExecutionPlan(
                resolution.getLimits(), baseKey, baseKey, algorithm, fallback,
                resourceCode, resolution.getPolicySource(), resolution.getPolicyRevision());
        SmartRedisLimiterResult result = algorithmInstance.tryAcquireWithResult(
                context, plan, keyStrategy);
        return new SmartRedisLimiterExecutionOutcome(plan, result);
    }

    private String resolveSubjectOnce(SmartRedisLimiterContext context, String keyStrategy) {
        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute attribute =
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute
                        .PRECOMPUTED_KEY_PART;
        String precomputed = context.getAttribute(attribute);
        if (precomputed != null && !precomputed.trim().isEmpty()) {
            context.setAttribute(attribute, null);
            return precomputed;
        }
        SmartRedisLimiterAlgorithm algorithm = algorithmFactory.getAlgorithm(
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant.ALGORITHM_FIXED);
        SmartRedisLimiterKeyGenerator generator = algorithm.getKeyGenerator(keyStrategy);
        return generator.generate(context);
    }
}
