package io.github.surezzzzzz.sdk.retry.redis.smart.constant;

import lombok.Getter;

/**
 * Redis 失败处理策略
 *
 * @author surezzzzzz
 */
@Getter
public enum RedisFailureStrategy {

    /**
     * Redis 失败时拒绝重试
     */
    FAIL_CLOSED("fail_closed", "Redis 失败时不允许重试"),
    /**
     * Redis 失败时允许重试
     */
    FAIL_OPEN("fail_open", "Redis 失败时允许重试"),
    /**
     * Redis 失败时抛出异常
     */
    THROW("throw", "Redis 失败时抛出异常");

    /**
     * 策略代码
     */
    private final String code;
    /**
     * 策略说明
     */
    private final String description;

    RedisFailureStrategy(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取失败处理策略。
     *
     * @param code 策略代码
     * @return 对应策略；不存在时返回 null
     */
    public static RedisFailureStrategy fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RedisFailureStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * 判断策略代码是否有效。
     *
     * @param code 策略代码
     * @return true 表示有效，false 表示无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部有效策略代码。
     *
     * @return 策略代码数组
     */
    public static String[] getAllCodes() {
        RedisFailureStrategy[] strategies = values();
        String[] codes = new String[strategies.length];
        for (int index = SmartRedisRetryConstant.ARRAY_INITIAL_INDEX;
             index < strategies.length;
             index++) {
            codes[index] = strategies[index].code;
        }
        return codes;
    }

    /**
     * 返回策略代码。
     *
     * @return 策略代码
     */
    @Override
    public String toString() {
        return code;
    }
}
