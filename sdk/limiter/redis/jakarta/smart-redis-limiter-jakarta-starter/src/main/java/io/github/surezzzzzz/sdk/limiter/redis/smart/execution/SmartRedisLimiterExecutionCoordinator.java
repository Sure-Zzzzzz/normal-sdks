package io.github.surezzzzzz.sdk.limiter.redis.smart.execution;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyGenerator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyResolution;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyResolver;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.*;
import org.springframework.beans.factory.ObjectProvider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
    private final SmartRedisLimiterTypedExecutionEngine typedEngine;
    private final Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> typedFactProviders;
    private final SmartRedisLimiterTypedPolicySnapshotStore typedSnapshotStore;
    private final List<SmartRedisLimiterTypedGateDeclaration> typedGateDeclarations;

    /**
     * 构造请求执行协调器（v1 专链，兼容旧装配）
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
        this(properties, algorithmFactory, snapshotStore, policyResolver,
                null, null, null, null);
    }

    /**
     * 构造请求执行协调器（含类型化专链）
     * <p>typed.enabled=true 时 execute 走类型化多维门禁；任一 typed 依赖缺失由装配层保证不启用。</p>
     *
     * @param properties            限流器配置
     * @param algorithmFactory      算法工厂
     * @param snapshotStore         可选 v1 快照存储
     * @param policyResolver        可选 v1 策略解析器
     * @param typedEngine           类型化执行引擎（typed 启用时非空）
     * @param typedFactProviders    身份维度事实提供方（按维度索引）
     * @param typedSnapshotStore    可选类型化快照存储（无 client 制品时为空，走本地声明）
     * @param typedGateDeclarations 门禁声明（已通过配置校验）
     */
    public SmartRedisLimiterExecutionCoordinator(
            SmartRedisLimiterProperties properties,
            SmartRedisLimiterAlgorithmFactory algorithmFactory,
            ObjectProvider<SmartRedisLimiterPolicySnapshotStore> snapshotStore,
            ObjectProvider<SmartRedisLimiterPolicyResolver> policyResolver,
            SmartRedisLimiterTypedExecutionEngine typedEngine,
            List<SmartRedisLimiterTypedFactProvider> typedFactProviders,
            SmartRedisLimiterTypedPolicySnapshotStore typedSnapshotStore,
            List<SmartRedisLimiterTypedGateDeclaration> typedGateDeclarations) {
        this.properties = properties;
        this.algorithmFactory = algorithmFactory;
        this.snapshotStore = snapshotStore.getIfAvailable();
        this.policyResolver = policyResolver.getIfAvailable();
        this.typedEngine = typedEngine;
        this.typedFactProviders = new EnumMap<>(SmartRedisLimiterDataDimension.class);
        if (typedFactProviders != null) {
            for (SmartRedisLimiterTypedFactProvider provider : typedFactProviders) {
                this.typedFactProviders.put(provider.dimension(), provider);
            }
        }
        this.typedSnapshotStore = typedSnapshotStore;
        this.typedGateDeclarations = typedGateDeclarations;
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
        if (typedEngine != null && Boolean.TRUE.equals(properties.getTyped().getEnabled())) {
            return executeTyped(context, resourceCode, keyStrategy, localLimits, algorithm, fallback);
        }
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

    /**
     * 类型化多维门禁执行：构建不可变计划后交给唯一类型化引擎
     *
     * @param context      限流上下文
     * @param resourceCode 稳定资源编码
     * @param keyStrategy  Key 生成策略（仅作算法桥接透传）
     * @param localLimits  入口本地限额（typed 模式不使用，声明本地限额在门禁声明中）
     * @param algorithm    入口算法（typed 模式统一取 typed.algorithm）
     * @param fallback     入口降级策略（typed 模式统一取 typed.redis-degradation）
     * @return 限流执行结果
     */
    private SmartRedisLimiterExecutionOutcome executeTyped(SmartRedisLimiterContext context,
                                                           String resourceCode,
                                                           String keyStrategy,
                                                           List<SmartRedisLimiterProperties.SmartLimitRule> localLimits,
                                                           String algorithm,
                                                           String fallback) {
        SmartRedisLimiterAcceptedTypedPolicySnapshot acceptedTyped = typedSnapshotStore == null
                ? null
                : typedSnapshotStore.getCurrent();
        SmartRedisLimiterTypedExecutionPlan plan = SmartRedisLimiterTypedPlanBuilder.build(
                properties.getMe(),
                resourceCode,
                typedGateDeclarations,
                typedFactProviders,
                acceptedTyped == null ? null : acceptedTyped.getSnapshot(),
                properties.getTyped().getExpectedPolicyEpoch(),
                context,
                Boolean.TRUE.equals(properties.getRedis().getUseHashTag()));
        SmartRedisLimiterTypedExecutionOutcome typedOutcome =
                typedEngine.execute(plan, context, resourceCode, keyStrategy);
        return toLegacyOutcome(typedOutcome, resourceCode);
    }

    /**
     * 将类型化执行结果映射回入口执行结果形态
     * <p>403/503 语义经限流异常的 fallback 原因字段表达：明确拒绝与事实不可用、Redis 拒绝降级
     * 均不放行（passed=false），首个拒绝门禁的维度写入 fallback 原因，供异常处理器区分 429/403/503。</p>
     */
    private SmartRedisLimiterExecutionOutcome toLegacyOutcome(
            SmartRedisLimiterTypedExecutionOutcome typedOutcome, String resourceCode) {
        if (typedOutcome.isPassed()) {
            SmartRedisLimiterExecutionPlan plan = new SmartRedisLimiterExecutionPlan(
                    java.util.Collections.<SmartRedisLimiterProperties.SmartLimitRule>emptyList(),
                    SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE,
                    SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE,
                    properties.getTyped().getAlgorithm(),
                    properties.getTyped().getRedisDegradation(),
                    resourceCode,
                    io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                            .POLICY_SOURCE_LOCAL,
                    null);
            return new SmartRedisLimiterExecutionOutcome(plan, SmartRedisLimiterResult.builder()
                    .passed(true).fallback(false).build());
        }
        String reason;
        if (typedOutcome.getStatus() == SmartRedisLimiterTypedExecutionStatus.ACCESS_DENIED) {
            reason = "typed_access_denied:" + typedOutcome.getRejectedDimension();
        } else if (typedOutcome.getStatus() == SmartRedisLimiterTypedExecutionStatus.DUPLICATE_BUCKET) {
            reason = "typed_duplicate_bucket:" + typedOutcome.getRejectedDimension();
        } else if (typedOutcome.getStatus() == SmartRedisLimiterTypedExecutionStatus.DEGRADE_DENIED) {
            reason = "typed_degrade_denied:" + typedOutcome.getRejectedDimension();
        } else if (typedOutcome.getStatus() == SmartRedisLimiterTypedExecutionStatus.UNAVAILABLE) {
            reason = "typed_fact_unavailable:" + typedOutcome.getRejectedDimension();
        } else {
            reason = "typed_limit_exceeded:" + typedOutcome.getRejectedDimension();
        }
        SmartRedisLimiterResult result = typedOutcome.getRejectedResult() != null
                ? typedOutcome.getRejectedResult()
                : SmartRedisLimiterResult.builder().passed(false).fallback(
                        typedOutcome.getStatus() != SmartRedisLimiterTypedExecutionStatus.LIMIT_EXCEEDED)
                .fallbackReason(reason).build();
        if (typedOutcome.getRejectedResult() != null
                && typedOutcome.getStatus() != SmartRedisLimiterTypedExecutionStatus.LIMIT_EXCEEDED) {
            result = SmartRedisLimiterResult.builder().passed(false).fallback(true)
                    .fallbackReason(reason).build();
        }
        SmartRedisLimiterExecutionPlan plan = new SmartRedisLimiterExecutionPlan(
                java.util.Collections.<SmartRedisLimiterProperties.SmartLimitRule>emptyList(),
                SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE,
                SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE,
                properties.getTyped().getAlgorithm(),
                properties.getTyped().getRedisDegradation(),
                resourceCode,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                        .POLICY_SOURCE_LOCAL,
                null);
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
