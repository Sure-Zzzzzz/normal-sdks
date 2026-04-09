package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;

/**
 * Token Event Type
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
     * Token 删除（Spring Authorization Server 内部触发）
     */
    REMOVED("removed", "Token 删除"),

    /**
     * Token 自省（/oauth2/introspect）
     */
    INTROSPECTED("introspected", "Token 自省");

    private final String code;
    private final String description;

    TokenEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String toString() {
        return code;
    }
}
