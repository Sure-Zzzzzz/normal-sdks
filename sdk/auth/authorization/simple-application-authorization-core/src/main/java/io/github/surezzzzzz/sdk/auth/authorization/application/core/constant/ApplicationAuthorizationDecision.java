package io.github.surezzzzzz.sdk.auth.authorization.application.core.constant;

import lombok.Getter;

/**
 * 应用授权判定结果。
 *
 * @author surezzzzzz
 */
@Getter
public enum ApplicationAuthorizationDecision {

    /**
     * 允许访问。
     */
    ALLOW("ALLOW", "允许访问"),
    /**
     * 拒绝访问。
     */
    DENY("DENY", "拒绝访问");

    /**
     * 判定代码。
     */
    private final String code;
    /**
     * 判定说明。
     */
    private final String description;

    ApplicationAuthorizationDecision(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取判定。
     *
     * @param code 判定代码
     * @return 判定；不存在时返回 null
     */
    public static ApplicationAuthorizationDecision fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ApplicationAuthorizationDecision decision : values()) {
            if (decision.code.equals(code)) {
                return decision;
            }
        }
        return null;
    }

    /**
     * 判断判定代码是否有效。
     *
     * @param code 判定代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部判定代码。
     *
     * @return 判定代码数组
     */
    public static String[] getAllCodes() {
        ApplicationAuthorizationDecision[] decisions = values();
        String[] codes = new String[decisions.length];
        for (int index = 0; index < decisions.length; index++) {
            codes[index] = decisions[index].code;
        }
        return codes;
    }

    /**
     * 返回判定代码。
     *
     * @return 判定代码
     */
    @Override
    public String toString() {
        return code;
    }
}
