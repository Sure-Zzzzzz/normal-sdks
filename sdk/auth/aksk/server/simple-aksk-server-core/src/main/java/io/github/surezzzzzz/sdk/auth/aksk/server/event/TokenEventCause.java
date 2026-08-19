package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;

/**
 * Token 生命周期事件原因。
 *
 * <p>事件类型描述发生了什么，原因描述触发该事件的业务来源。
 *
 * @author surezzzzzz
 */
@Getter
public enum TokenEventCause {

    /**
     * 未指定原因，用于兼容旧事件构造器。
     */
    UNSPECIFIED("unspecified", "未指定"),

    /**
     * OAuth2 撤销端点。
     */
    OAUTH2_REVOKE("oauth2-revoke", "OAuth2 撤销"),

    /**
     * Token 管理操作。
     */
    TOKEN_MANAGEMENT("token-management", "Token 管理撤销"),

    /**
     * 应用授权被完整替换。
     */
    APPLICATION_AUTHORIZATION_REPLACED("application-authorization-replaced", "应用授权完整替换"),

    /**
     * 应用授权被撤销。
     */
    APPLICATION_AUTHORIZATION_REVOKED("application-authorization-revoked", "应用授权撤销"),

    /**
     * Client 被禁用。
     */
    CLIENT_DISABLED("client-disabled", "Client 禁用"),

    /**
     * Client 被删除。
     */
    CLIENT_DELETED("client-deleted", "Client 删除"),

    /**
     * Client Secret 重置。
     */
    CLIENT_SECRET_RESET("client-secret-reset", "Client Secret 重置");

    /**
     * 用于传输、持久化和审计检索的稳定原因编码。
     */
    private final String code;

    /**
     * 面向展示的中文说明，不应用于程序分支。
     */
    private final String description;

    TokenEventCause(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取原因枚举。
     *
     * @param code 原因 code
     * @return 原因枚举，不存在返回 null
     */
    public static TokenEventCause fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TokenEventCause cause : values()) {
            if (cause.code.equalsIgnoreCase(code)) {
                return cause;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return code;
    }
}
