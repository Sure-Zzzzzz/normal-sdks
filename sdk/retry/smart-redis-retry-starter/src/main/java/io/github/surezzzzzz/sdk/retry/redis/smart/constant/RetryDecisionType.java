package io.github.surezzzzzz.sdk.retry.redis.smart.constant;

import lombok.Getter;

/**
 * 重试决策类型
 *
 * @author surezzzzzz
 */
@Getter
public enum RetryDecisionType {

    /**
     * 允许执行
     */
    ALLOW("allow", "允许执行"),
    /**
     * 尚未到达下次重试时间
     */
    WAITING("waiting", "未到下次重试时间"),
    /**
     * 重试次数已经耗尽
     */
    EXHAUSTED("exhausted", "重试次数已耗尽");

    /**
     * 决策代码
     */
    private final String code;
    /**
     * 决策说明
     */
    private final String description;

    RetryDecisionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取重试决策类型。
     *
     * @param code 决策代码
     * @return 对应决策类型；不存在时返回 null
     */
    public static RetryDecisionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RetryDecisionType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断决策代码是否有效。
     *
     * @param code 决策代码
     * @return true 表示有效，false 表示无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部有效决策代码。
     *
     * @return 决策代码数组
     */
    public static String[] getAllCodes() {
        RetryDecisionType[] types = values();
        String[] codes = new String[types.length];
        for (int index = SmartRedisRetryConstant.ARRAY_INITIAL_INDEX;
             index < types.length;
             index++) {
            codes[index] = types[index].code;
        }
        return codes;
    }

    /**
     * 返回决策代码。
     *
     * @return 决策代码
     */
    @Override
    public String toString() {
        return code;
    }
}
