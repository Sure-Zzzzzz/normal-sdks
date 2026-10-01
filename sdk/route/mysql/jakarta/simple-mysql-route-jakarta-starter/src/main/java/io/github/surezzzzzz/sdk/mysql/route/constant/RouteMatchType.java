package io.github.surezzzzzz.sdk.mysql.route.constant;

import lombok.Getter;

import java.util.Locale;

/**
 * MySQL Route 规则匹配类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum RouteMatchType {
    EXACT("exact", "精确匹配"),
    PREFIX("prefix", "前缀匹配"),
    SUFFIX("suffix", "后缀匹配"),
    WILDCARD("wildcard", "通配符匹配"),
    REGEX("regex", "正则匹配");

    private final String code;
    private final String description;

    RouteMatchType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按编码解析匹配类型。
     *
     * @param code 匹配类型编码
     * @return 对应匹配类型；编码无效时返回 {@code null}
     */
    public static RouteMatchType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (RouteMatchType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断编码是否为受支持的匹配类型。
     *
     * @param code 匹配类型编码
     * @return 编码有效时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部受支持的匹配类型编码。
     *
     * @return 匹配类型编码数组
     */
    public static String[] getAllCodes() {
        RouteMatchType[] types = values();
        String[] codes = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            codes[index] = types[index].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
