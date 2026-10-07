package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;

import java.util.*;

/**
 * 类型化执行计划构建器
 * <p>在任何扣减前完成：维度适用性判定（事实四态）、USER/SERVICE 择一冲突检查、
 * 每门禁 EXACT→DEFAULT→本地声明的整体限额选择（快照代次不符按无快照处理，执行本地门禁）、
 * 同桶唯一性校验。计划不可变，执行期间不再取值或替换规则。</p>
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterTypedPlanBuilder {

    /**
     * 固定执行顺序：IP、RESOURCE、CUSTOMER、USER/SERVICE、CREDENTIAL、CUSTOM
     */
    private static final List<SmartRedisLimiterDataDimension> EXECUTION_ORDER = Collections.unmodifiableList(
            Arrays.asList(SmartRedisLimiterDataDimension.IP,
                    SmartRedisLimiterDataDimension.RESOURCE,
                    SmartRedisLimiterDataDimension.CUSTOMER,
                    SmartRedisLimiterDataDimension.USER,
                    SmartRedisLimiterDataDimension.SERVICE,
                    SmartRedisLimiterDataDimension.CREDENTIAL,
                    SmartRedisLimiterDataDimension.CUSTOM));

    /**
     * 需要宿主事实提供方的身份维度
     */
    private static final Set<SmartRedisLimiterDataDimension> FACT_DIMENSIONS = Collections.unmodifiableSet(
            EnumSet.of(SmartRedisLimiterDataDimension.USER,
                    SmartRedisLimiterDataDimension.SERVICE,
                    SmartRedisLimiterDataDimension.CREDENTIAL,
                    SmartRedisLimiterDataDimension.CUSTOMER,
                    SmartRedisLimiterDataDimension.CUSTOM));

    private SmartRedisLimiterTypedPlanBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建一次请求的类型化执行计划
     *
     * @param serviceCode   服务编码（宿主 me）
     * @param resourceCode  稳定资源编码
     * @param declarations  宿主门禁声明
     * @param providers     身份维度事实提供方（按维度索引）
     * @param snapshot      已接受类型化快照；null 或代次不符按无快照处理
     * @param expectedEpoch 部署时明确预期的策略代次
     * @param context       限流上下文
     * @param useHashTag    计数桶是否使用 Hash Tag
     * @return 不可变执行计划；资格不满足时携带扣减前拒绝
     */
    public static SmartRedisLimiterTypedExecutionPlan build(String serviceCode,
                                                            String resourceCode,
                                                            List<SmartRedisLimiterTypedGateDeclaration> declarations,
                                                            Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> providers,
                                                            SmartRedisLimiterTypedPolicySnapshot snapshot,
                                                            Long expectedEpoch,
                                                            SmartRedisLimiterContext context,
                                                            boolean useHashTag) {
        List<SmartRedisLimiterTypedGateDeclaration> ordered = new ArrayList<>(declarations);
        ordered.sort(Comparator.comparingInt(d -> EXECUTION_ORDER.indexOf(d.getDimension())));

        SmartRedisLimiterTypedPolicySnapshot usableSnapshot = usableSnapshot(snapshot, serviceCode, expectedEpoch);
        List<SmartRedisLimiterTypedGateRule> gates = new ArrayList<>(ordered.size());
        Set<String> bucketDigests = new HashSet<>();
        boolean userApplicable = false;
        boolean serviceApplicable = false;

        for (SmartRedisLimiterTypedGateDeclaration declaration : ordered) {
            SmartRedisLimiterDataDimension dimension = declaration.getDimension();
            String objectId;
            if (dimension == SmartRedisLimiterDataDimension.RESOURCE) {
                objectId = null;
            } else if (dimension == SmartRedisLimiterDataDimension.IP) {
                String clientIp = context == null ? null : context.getClientIp();
                if (clientIp == null || clientIp.trim().isEmpty()
                        || SmartRedisLimiterConstant.IP_UNKNOWN.equalsIgnoreCase(clientIp.trim())) {
                    return rejected(SmartRedisLimiterTypedFactStatus.UNAVAILABLE, dimension, "trusted-client-ip-missing");
                }
                objectId = clientIp.trim();
            } else {
                SmartRedisLimiterTypedFactProvider provider = providers == null ? null : providers.get(dimension);
                if (provider == null) {
                    return rejected(SmartRedisLimiterTypedFactStatus.UNAVAILABLE, dimension, "fact-provider-missing");
                }
                SmartRedisLimiterTypedDimensionFact fact = provider.provide(context);
                if (fact == null) {
                    fact = SmartRedisLimiterTypedDimensionFact.of(SmartRedisLimiterTypedFactStatus.UNAVAILABLE);
                }
                if (fact.getStatus() == SmartRedisLimiterTypedFactStatus.DENIED) {
                    return rejected(fact.getStatus(), dimension, "fact-denied");
                }
                if (fact.getStatus() == SmartRedisLimiterTypedFactStatus.UNAVAILABLE) {
                    return rejected(fact.getStatus(), dimension, "fact-unavailable");
                }
                if (fact.getStatus() == SmartRedisLimiterTypedFactStatus.NOT_APPLICABLE) {
                    continue;
                }
                objectId = fact.getObjectId();
                if (dimension == SmartRedisLimiterDataDimension.USER) {
                    userApplicable = true;
                }
                if (dimension == SmartRedisLimiterDataDimension.SERVICE) {
                    serviceApplicable = true;
                }
            }

            ResolvedLimits resolved = resolveLimits(usableSnapshot, resourceCode, declaration, objectId);
            SmartRedisLimiterTypedGateRule gate = new SmartRedisLimiterTypedGateRule(
                    serviceCode, resourceCode, dimension,
                    declaration.getNamespace(), declaration.getCustomType(), objectId,
                    resolved.limits, resolved.source, useHashTag);
            if (!bucketDigests.add(gate.getBucketDigest())) {
                return new SmartRedisLimiterTypedExecutionPlan(gates,
                        new SmartRedisLimiterTypedRejection(
                                SmartRedisLimiterTypedFactStatus.UNAVAILABLE, dimension, "duplicate-bucket"));
            }
            gates.add(gate);
        }

        if (userApplicable && serviceApplicable) {
            return new SmartRedisLimiterTypedExecutionPlan(gates, new SmartRedisLimiterTypedRejection(
                    SmartRedisLimiterTypedFactStatus.UNAVAILABLE, SmartRedisLimiterDataDimension.SERVICE,
                    "user-service-conflict"));
        }
        return new SmartRedisLimiterTypedExecutionPlan(gates, null);
    }

    private static SmartRedisLimiterTypedPolicySnapshot usableSnapshot(SmartRedisLimiterTypedPolicySnapshot snapshot,
                                                                       String serviceCode,
                                                                       Long expectedEpoch) {
        if (snapshot == null || expectedEpoch == null
                || snapshot.getPolicyEpoch() != expectedEpoch
                || !snapshot.getServiceCode().equals(serviceCode)) {
            return null;
        }
        return snapshot;
    }

    private static ResolvedLimits resolveLimits(SmartRedisLimiterTypedPolicySnapshot snapshot,
                                                String resourceCode,
                                                SmartRedisLimiterTypedGateDeclaration declaration,
                                                String objectId) {
        if (snapshot != null) {
            for (SmartRedisLimiterTypedPolicy rule : snapshot.getRules()) {
                if (!rule.isEnabled()
                        || !rule.getKey().getResourceCode().equals(resourceCode)
                        || rule.getKey().getDimension() != declaration.getDimension()
                        || !rule.getKey().getNamespace().equals(declaration.getNamespace())) {
                    continue;
                }
                String ruleCustomType = rule.getKey().getCustomType();
                String declaredCustomType = declaration.getCustomType();
                if (ruleCustomType == null ? declaredCustomType != null : !ruleCustomType.equals(declaredCustomType)) {
                    continue;
                }
                if (rule.getKey().getSelector() == SmartRedisLimiterRuleSelector.EXACT
                        && rule.getKey().getObjectId() != null
                        && rule.getKey().getObjectId().equals(objectId)) {
                    return new ResolvedLimits(toRules(rule.getLimits()),
                            SmartRedisLimiterStarterConstant.TYPED_SOURCE_EXACT);
                }
            }
            for (SmartRedisLimiterTypedPolicy rule : snapshot.getRules()) {
                if (!rule.isEnabled()
                        || !rule.getKey().getResourceCode().equals(resourceCode)
                        || rule.getKey().getDimension() != declaration.getDimension()
                        || rule.getKey().getSelector() != SmartRedisLimiterRuleSelector.DEFAULT
                        || !rule.getKey().getNamespace().equals(declaration.getNamespace())) {
                    continue;
                }
                String ruleCustomType = rule.getKey().getCustomType();
                String declaredCustomType = declaration.getCustomType();
                if (ruleCustomType == null ? declaredCustomType != null : !ruleCustomType.equals(declaredCustomType)) {
                    continue;
                }
                return new ResolvedLimits(toRules(rule.getLimits()),
                        SmartRedisLimiterStarterConstant.TYPED_SOURCE_DEFAULT);
            }
        }
        return new ResolvedLimits(declaration.getLocalLimits(), SmartRedisLimiterStarterConstant.TYPED_SOURCE_LOCAL);
    }

    private static List<SmartRedisLimiterProperties.SmartLimitRule> toRules(List<SmartRedisLimiterLimit> limits) {
        List<SmartRedisLimiterProperties.SmartLimitRule> converted = new ArrayList<>(limits.size());
        for (SmartRedisLimiterLimit limit : limits) {
            SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
            rule.setCount(limit.getCount());
            rule.setWindow(limit.getWindow());
            rule.setUnit(limit.getUnit());
            converted.add(rule);
        }
        return converted;
    }

    private static SmartRedisLimiterTypedExecutionPlan rejected(SmartRedisLimiterTypedFactStatus status,
                                                                SmartRedisLimiterDataDimension dimension,
                                                                String reason) {
        return new SmartRedisLimiterTypedExecutionPlan(
                Collections.<SmartRedisLimiterTypedGateRule>emptyList(),
                new SmartRedisLimiterTypedRejection(status, dimension, reason));
    }

    private static final class ResolvedLimits {
        private final List<SmartRedisLimiterProperties.SmartLimitRule> limits;
        private final String source;

        private ResolvedLimits(List<SmartRedisLimiterProperties.SmartLimitRule> limits, String source) {
            this.limits = limits;
            this.source = source;
        }
    }
}
