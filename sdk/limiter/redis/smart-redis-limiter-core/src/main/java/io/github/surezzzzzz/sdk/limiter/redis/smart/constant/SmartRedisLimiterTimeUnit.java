package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter 动态策略时间单位
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterTimeUnit {

    /**
     * 秒
     */
    SECONDS("SECONDS", "秒", 1L),

    /**
     * 分钟
     */
    MINUTES("MINUTES", "分钟", 60L),

    /**
     * 小时
     */
    HOURS("HOURS", "小时", 3600L),

    /**
     * 天
     */
    DAYS("DAYS", "天", 86400L);

    /**
     * 协议编码
     */
    private final String code;

    /**
     * 描述
     */
    private final String description;

    /**
     * 单位秒数
     */
    private final long seconds;

    /**
     * 根据协议编码获取时间单位
     *
     * @param code 协议编码
     * @return 时间单位，不存在时返回 null
     */
    public static SmartRedisLimiterTimeUnit fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmartRedisLimiterTimeUnit unit : values()) {
            if (unit.code.equalsIgnoreCase(code)) {
                return unit;
            }
        }
        return null;
    }

    /**
     * 判断协议编码是否有效
     *
     * @param code 协议编码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有协议编码
     *
     * @return 协议编码数组
     */
    public static String[] getAllCodes() {
        SmartRedisLimiterTimeUnit[] units = values();
        String[] codes = new String[units.length];
        for (int i = 0; i < units.length; i++) {
            codes[i] = units[i].code;
        }
        return codes;
    }

    /**
     * 将窗口换算为秒
     *
     * @param window 时间窗口
     * @return 时间窗口秒数
     */
    public long toSeconds(long window) {
        if (window <= 0) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_LIMIT_INVALID,
                    String.format(ErrorMessage.POLICY_LIMIT_INVALID,
                            SmartRedisLimiterConstant.POLICY_FIELD_WINDOW,
                            ErrorMessage.REASON_FIELD_MUST_BE_POSITIVE));
        }
        try {
            return Math.multiplyExact(window, seconds);
        } catch (ArithmeticException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_WINDOW_OVERFLOW,
                    ErrorMessage.POLICY_WINDOW_OVERFLOW,
                    ex);
        }
    }

    /**
     * 获取协议编码
     *
     * @return 协议编码
     */
    @Override
    public String toString() {
        return code;
    }
}
