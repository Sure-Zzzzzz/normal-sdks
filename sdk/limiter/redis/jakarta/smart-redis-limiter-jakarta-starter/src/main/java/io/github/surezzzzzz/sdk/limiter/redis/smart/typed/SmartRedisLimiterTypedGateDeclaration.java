package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 类型化门禁宿主声明
 * <p>宿主按稳定 resourceCode 为每个执行的维度声明固定命名空间（CUSTOM 额外声明 customType）
 * 与完整本地限额。每个门禁必须提供完整本地 limits：无快照、空快照或远程规则缺失时执行本地门禁，
 * 不引入另一个 remote-required 开关。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedGateDeclaration {

    /**
     * 计数维度
     */
    private final SmartRedisLimiterDataDimension dimension;

    /**
     * 固定命名空间（宿主门禁声明，不随认证来源变化）
     */
    private final String namespace;

    /**
     * 自定义类型（仅 CUSTOM 维度非空）
     */
    private final String customType;

    /**
     * 完整本地限额窗口
     */
    private final List<SmartRedisLimiterProperties.SmartLimitRule> localLimits;

    /**
     * 构造门禁声明
     *
     * @param dimension   计数维度
     * @param namespace   固定命名空间
     * @param customType  自定义类型
     * @param localLimits 完整本地限额窗口
     * @throws SmartRedisLimiterException 声明字段或组合非法时抛出
     */
    public SmartRedisLimiterTypedGateDeclaration(SmartRedisLimiterDataDimension dimension,
                                                 String namespace,
                                                 String customType,
                                                 List<SmartRedisLimiterProperties.SmartLimitRule> localLimits) {
        if (dimension == null) {
            throw declarationInvalid(ErrorMessage.REASON_TYPED_DIMENSION_INVALID);
        }
        List<SmartRedisLimiterLimit> coreLimits = new ArrayList<>();
        if (localLimits != null) {
            for (SmartRedisLimiterProperties.SmartLimitRule rule : localLimits) {
                if (rule == null) {
                    throw declarationInvalid(ErrorMessage.REASON_TYPED_LIMITS_EMPTY);
                }
                coreLimits.add(new SmartRedisLimiterLimit(
                        rule.getCount(), rule.getWindow(), rule.getUnit()));
            }
        }
        this.dimension = dimension;
        this.namespace = io.github.surezzzzzz.sdk.limiter.redis.smart.support
                .SmartRedisLimiterTypedPolicyValidationHelper.normalizeNamespace(namespace);
        this.customType = io.github.surezzzzzz.sdk.limiter.redis.smart.support
                .SmartRedisLimiterTypedPolicyValidationHelper.normalizeCustomType(dimension, customType);
        io.github.surezzzzzz.sdk.limiter.redis.smart.support
                .SmartRedisLimiterTypedPolicyValidationHelper.validateLimits(coreLimits);
        this.localLimits = Collections.unmodifiableList(
                localLimits == null ? Collections.<SmartRedisLimiterProperties.SmartLimitRule>emptyList()
                        : new ArrayList<>(localLimits));
    }

    private static SmartRedisLimiterException declarationInvalid(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.TYPED_POLICY_KEY_INVALID,
                String.format(ErrorMessage.TYPED_POLICY_KEY_INVALID, "typed-gate", reason));
    }
}
