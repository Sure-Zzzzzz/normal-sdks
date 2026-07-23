package io.github.surezzzzzz.sdk.kafka.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * Kafka 路由匹配类型
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum RouteMatchType {

    /**
     * 全等匹配
     */
    EXACT("exact", "全等匹配"),

    /**
     * 前缀匹配
     */
    PREFIX("prefix", "前缀匹配"),

    /**
     * 后缀匹配
     */
    SUFFIX("suffix", "后缀匹配"),

    /**
     * 通配符匹配
     */
    WILDCARD("wildcard", "通配符匹配"),

    /**
     * 正则匹配
     */
    REGEX("regex", "正则匹配");

    private final String code;
    private final String description;

    public static RouteMatchType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.toLowerCase(Locale.ROOT).trim();
        for (RouteMatchType type : values()) {
            if (type.code.equals(lowerCode)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        RouteMatchType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
