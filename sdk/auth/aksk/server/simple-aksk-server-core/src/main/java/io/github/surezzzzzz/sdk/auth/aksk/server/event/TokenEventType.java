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

    /**
     * 判断 code 是否有效
     *
     * @param code 事件类型 code
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的 code
     *
     * @return code 数组
     */
    public static String[] getAllCodes() {
        TokenEventType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }
}
