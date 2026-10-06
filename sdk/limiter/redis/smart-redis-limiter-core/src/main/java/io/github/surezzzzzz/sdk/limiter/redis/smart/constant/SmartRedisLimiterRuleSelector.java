package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.Getter;

/**
 * SmartRedisLimiter 类型化规则选择器
 * <p>DEFAULT 表示该维度下每个实际对象各自使用同一套限额，不共享计数桶；
 * EXACT 表示仅整体覆盖一个精确对象的本维度限额。字面量 {@code *} 在 EXACT 中仍是普通对象，不承担通配语义。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum SmartRedisLimiterRuleSelector {

    /**
     * 默认规则：每个实际对象各自的默认限额
     */
    DEFAULT("DEFAULT", "默认规则"),

    /**
     * 精确规则：整体覆盖单个对象的限额
     */
    EXACT("EXACT", "精确规则");

    /**
     * 选择器编码（JSON 协议值）
     */
    private final String code;

    /**
     * 选择器说明
     */
    private final String description;

    SmartRedisLimiterRuleSelector(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按编码解析选择器
     *
     * @param code 选择器编码
     * @return 对应选择器；未识别时返回 null，由调用方执行失败关闭
     */
    public static SmartRedisLimiterRuleSelector fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmartRedisLimiterRuleSelector selector : values()) {
            if (selector.code.equals(code)) {
                return selector;
            }
        }
        return null;
    }

    /**
     * 判断编码是否为合法选择器
     *
     * @param code 选择器编码
     * @return 编码合法时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
