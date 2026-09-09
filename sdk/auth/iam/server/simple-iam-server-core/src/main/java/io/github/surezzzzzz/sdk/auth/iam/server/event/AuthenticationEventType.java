package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 认证事件类型（登录 / 登出 / 账号锁定）。
 *
 * @author surezzzzzz
 */
@Getter
public enum AuthenticationEventType {

    /**
     * 登录成功（本地密码 / 外部身份源）
     */
    LOGIN_SUCCEEDED("login-succeeded", "登录成功"),
    /**
     * 登录失败（凭据错误 / 账号禁用或锁定 / 外部源故障等）
     */
    LOGIN_FAILED("login-failed", "登录失败"),
    /**
     * 主动登出
     */
    LOGOUT("logout", "登出"),
    /**
     * 连续登录失败达到阈值触发账号锁定
     */
    ACCOUNT_LOCKED("account-locked", "账号锁定");

    private final String code;
    private final String description;

    AuthenticationEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 事件类型 code
     * @return 枚举，不存在返回 null
     */
    public static AuthenticationEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AuthenticationEventType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
