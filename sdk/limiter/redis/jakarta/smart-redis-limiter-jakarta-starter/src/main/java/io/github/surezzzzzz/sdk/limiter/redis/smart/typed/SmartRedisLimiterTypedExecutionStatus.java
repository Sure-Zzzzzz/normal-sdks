package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import lombok.Getter;

/**
 * 类型化执行状态
 *
 * @author surezzzzzz
 */
@Getter
public enum SmartRedisLimiterTypedExecutionStatus {

    /**
     * 全部门禁取得额度
     */
    PASSED("PASSED", "放行"),

    /**
     * 首个拒绝门禁额度耗尽（429）
     */
    LIMIT_EXCEEDED("LIMIT_EXCEEDED", "额度耗尽"),

    /**
     * 权威明确拒绝（403）
     */
    ACCESS_DENIED("ACCESS_DENIED", "明确拒绝"),

    /**
     * 必要事实不可用或过期（503）
     */
    UNAVAILABLE("UNAVAILABLE", "事实不可用"),

    /**
     * Redis 拒绝降级（503）
     */
    DEGRADE_DENIED("DEGRADE_DENIED", "Redis拒绝降级"),

    /**
     * 同一计划包含同桶两次（宿主配置错误，扣减前拒绝）
     */
    DUPLICATE_BUCKET("DUPLICATE_BUCKET", "同桶重复声明");

    private final String code;

    private final String description;

    SmartRedisLimiterTypedExecutionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
