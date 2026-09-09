package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM Token 生命周期事件业务原因。
 *
 * <p>与 {@link TokenEventType} 正交：类型表达动作，原因表达触发来源；
 * 新增来源只扩展本枚举，不改变按类型消费的分支。
 *
 * @author surezzzzzz
 */
@Getter
public enum TokenEventCause {

    /**
     * 未指定原因，用于兼容无来源的发布点。
     */
    UNSPECIFIED("unspecified", "未指定"),
    /**
     * OAuth2 标准 revoke 端点撤销。
     */
    OAUTH2_REVOKE("oauth2-revoke", "OAuth2 标准撤销"),
    /**
     * 资源验证客户端调用受控验证端点。
     */
    RESOURCE_VERIFICATION("resource-verification", "资源验证终审"),
    /**
     * 应用授权被完整替换。
     */
    APPLICATION_AUTHORIZATION_REPLACED("application-authorization-replaced", "应用授权完整替换"),
    /**
     * 应用授权被撤销。
     */
    APPLICATION_AUTHORIZATION_REVOKED("application-authorization-revoked", "应用授权撤销"),
    /**
     * IAM 会话被吊销连带失效。
     */
    SESSION_REVOKED("session-revoked", "会话吊销连带"),
    /**
     * Refresh Token 复用检测命中，整族撤销。
     */
    REFRESH_TOKEN_REUSE("refresh-token-reuse", "Refresh Token 复用检测");
    private final String code;
    private final String description;

    TokenEventCause(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 原因 code
     * @return 枚举，不存在返回 null
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
}
