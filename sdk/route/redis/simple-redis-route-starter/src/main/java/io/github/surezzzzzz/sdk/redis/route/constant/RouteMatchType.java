package io.github.surezzzzzz.sdk.redis.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Redis 路由匹配类型
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

    /**
     * 按配置编码解析路由匹配类型。
     *
     * @param code 匹配类型编码，大小写不敏感
     * @return 对应的匹配类型；编码为空或不受支持时返回 null
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
     * 判断配置编码是否对应受支持的路由匹配类型。
     *
     * @param code 匹配类型编码
     * @return 编码受支持时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部受支持的路由匹配类型编码。
     *
     * @return 按枚举声明顺序排列的匹配类型编码
     */
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
