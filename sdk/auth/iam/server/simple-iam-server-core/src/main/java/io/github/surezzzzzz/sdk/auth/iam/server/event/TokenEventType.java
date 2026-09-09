package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM Token 生命周期事件类型。
 *
 * <p>类型只表达发生的动作；撤销等动作的业务来源由 {@link TokenEventCause} 单独表达，
 * 不应为来源扩展新的类型值。
 *
 * @author surezzzzzz
 */
@Getter
public enum TokenEventType {

    /**
     * Token 颁发（/oauth2/token）
     */
    ISSUED("issued", "Token 颁发"),
    /**
     * Token 撤销（/oauth2/revoke）
     */
    REVOKED("revoked", "Token 撤销"),
    /**
     * Token 删除（授权记录移除时连带）
     */
    REMOVED("removed", "Token 删除"),
    /**
     * Token 受控验证终审（/iam/resource/tokens/verify）
     */
    VERIFIED("verified", "Token 受控验证"),
    /**
     * Refresh Token 复用检测命中（整族撤销的安全信号）
     */
    REUSE_DETECTED("reuse-detected", "Refresh Token 复用检测");
    private final String code;
    private final String description;

    TokenEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 事件类型 code
     * @return 枚举，不存在返回 null
     */
    public static TokenEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TokenEventType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
