package io.github.surezzzzzz.sdk.auth.resource.core.constant;

import lombok.Getter;

/**
 * 资源认证结果状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum ResourceAuthenticationOutcome {

    /**
     * 已完成认证。
     */
    AUTHENTICATED("AUTHENTICATED", "已完成认证"),
    /**
     * 已选来源认证拒绝。
     */
    REJECTED("REJECTED", "认证拒绝"),
    /**
     * 当前适配器不适用。
     */
    NOT_APPLICABLE("NOT_APPLICABLE", "不适用");

    /**
     * 状态代码。
     */
    private final String code;
    /**
     * 状态说明。
     */
    private final String description;

    ResourceAuthenticationOutcome(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取状态。
     *
     * @param code 状态代码
     * @return 状态；不存在时返回null
     */
    public static ResourceAuthenticationOutcome fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ResourceAuthenticationOutcome outcome : values()) {
            if (outcome.code.equals(code)) {
                return outcome;
            }
        }
        return null;
    }

    /**
     * 判断状态代码是否有效。
     *
     * @param code 状态代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部状态代码。
     *
     * @return 状态代码数组
     */
    public static String[] getAllCodes() {
        ResourceAuthenticationOutcome[] outcomes = values();
        String[] codes = new String[outcomes.length];
        for (int index = 0; index < outcomes.length; index++) {
            codes[index] = outcomes[index].code;
        }
        return codes;
    }

    /**
     * 返回状态代码。
     *
     * @return 状态代码
     */
    @Override
    public String toString() {
        return code;
    }
}
