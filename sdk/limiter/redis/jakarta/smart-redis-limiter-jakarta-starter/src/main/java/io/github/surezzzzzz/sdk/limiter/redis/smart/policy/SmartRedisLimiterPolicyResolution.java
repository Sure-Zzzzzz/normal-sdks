package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 请求级策略解析结果
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterPolicyResolution {

    /**
     * 最终完整限额列表
     */
    private final List<SmartRedisLimiterProperties.SmartLimitRule> limits;

    /**
     * 策略来源
     */
    private final String policySource;

    /**
     * 远程策略版本
     */
    private final Long policyRevision;

    /**
     * 构造策略解析结果
     *
     * @param limits         最终完整限额列表
     * @param policySource   策略来源
     * @param policyRevision 远程策略版本
     */
    public SmartRedisLimiterPolicyResolution(
            List<SmartRedisLimiterProperties.SmartLimitRule> limits,
            String policySource,
            Long policyRevision) {
        this.limits = Collections.unmodifiableList(new ArrayList<>(limits));
        this.policySource = policySource;
        this.policyRevision = policyRevision;
    }
}
