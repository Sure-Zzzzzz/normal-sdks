package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 路由规则匹配类型枚举
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum RouteMatchType {

    /**
     * 精确匹配
     */
    EXACT("exact", "精确匹配"),

    /**
     * 前缀匹配
     */
    PREFIX("prefix", "前缀匹配"),

    /**
     * 后缀匹配
     */
    SUFFIX("suffix", "后缀匹配"),

    /**
     * 通配符匹配（Ant风格）
     */
    WILDCARD("wildcard", "通配符匹配"),

    /**
     * 正则表达式匹配
     */
    REGEX("regex", "正则表达式匹配");

    private final String code;
    private final String description;

    /**
     * 根据代码获取枚举
     */
    public static RouteMatchType fromCode(String code) {
        if (code == null) {
            return null;
        }

        String lowerCode = code.toLowerCase().trim();
        for (RouteMatchType type : values()) {
            if (type.code.equals(lowerCode)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断代码是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
