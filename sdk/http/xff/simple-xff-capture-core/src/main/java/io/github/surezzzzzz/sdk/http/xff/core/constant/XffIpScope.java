package io.github.surezzzzzz.sdk.http.xff.core.constant;

import lombok.Getter;

/**
 * XFF IP 地址范围。
 *
 * @author surezzzzzz
 */
@Getter
public enum XffIpScope {

    /**
     * 全局公网单播地址。
     */
    PUBLIC("PUBLIC", "公网地址"),

    /**
     * RFC 1918 IPv4 私网或 RFC 4193 IPv6 ULA。
     */
    PRIVATE("PRIVATE", "私网地址"),

    /**
     * 合法但非公网、非私网的特殊用途地址。
     */
    SPECIAL("SPECIAL", "特殊用途地址"),

    /**
     * 非法 IP 字面量。
     */
    INVALID("INVALID", "非法 IP 字面量");

    private final String code;
    private final String description;

    XffIpScope(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取地址范围。
     *
     * @param code 范围代码
     * @return 地址范围，不存在时返回 null
     */
    public static XffIpScope fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (XffIpScope scope : values()) {
            if (scope.code.equals(code)) {
                return scope;
            }
        }
        return null;
    }

    /**
     * 判断范围代码是否有效。
     *
     * @param code 范围代码
     * @return true 表示有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部范围代码。
     *
     * @return 范围代码数组
     */
    public static String[] getAllCodes() {
        XffIpScope[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
