package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 会话撤销事件的业务来源。
 *
 * @author surezzzzzz
 */
@Getter
public enum SessionEventCause {

    /**
     * 来源未指定
     */
    UNSPECIFIED("unspecified", "未指定"),
    /**
     * 主动登出
     */
    LOGOUT("logout", "登出"),
    /**
     * 同一浏览器会话重新登录，踢撤销旧绑定会话
     */
    RELOGIN_KICK("relogin-kick", "重登踢旧会话"),
    /**
     * 用户生命周期批量吊销（禁用 / 删除 / 重置密码）
     */
    USER_LIFECYCLE("user-lifecycle", "用户生命周期批量吊销");

    private final String code;
    private final String description;

    SessionEventCause(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 业务原因 code
     * @return 枚举，不存在返回 null
     */
    public static SessionEventCause fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SessionEventCause cause : values()) {
            if (cause.code.equalsIgnoreCase(code)) {
                return cause;
            }
        }
        return null;
    }
}
