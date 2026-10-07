package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import lombok.Getter;

/**
 * 类型化维度事实状态
 * <p>宿主中立接口必须区分四种结果：有效事实、不适用、明确拒绝与不可用。
 * 空值或未分类异常一律按不可用处理，不能猜测成匿名身份或绕过门禁。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum SmartRedisLimiterTypedFactStatus {

    /**
     * 提供方给出经验证的稳定计数对象
     */
    PRESENT("PRESENT", "有效事实"),

    /**
     * 当前请求身份不适用该维度（如 SERVICE 请求不适用 USER）
     */
    NOT_APPLICABLE("NOT_APPLICABLE", "不适用"),

    /**
     * 权威确认无归属、停用、冲突归属或无资格，按 403 拒绝
     */
    DENIED("DENIED", "明确拒绝"),

    /**
     * 事实过期、提供方超时或不可用，按 503 拒绝
     */
    UNAVAILABLE("UNAVAILABLE", "不可用");

    private final String code;

    private final String description;

    SmartRedisLimiterTypedFactStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
