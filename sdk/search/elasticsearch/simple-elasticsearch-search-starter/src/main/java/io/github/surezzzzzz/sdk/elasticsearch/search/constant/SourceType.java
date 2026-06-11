package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

import lombok.Getter;

/**
 * 查询来源类型枚举
 * <p>
 * 由端点在调用 executor 前设置到 request.sourceType，
 * 随 EsQueryEvent / EsAggEvent 的 context 一起发布，供审计/监控扩展使用。
 *
 * @author surezzzzzz
 * @since 1.6.6
 */
@Getter
public enum SourceType {

    /**
     * 来自 /api/query 或 /api/agg 端点
     */
    QUERY_API("QUERY_API", "标准查询/聚合端点"),

    /**
     * 来自 /api/query/nl 或 /api/agg/nl 端点
     */
    NL_API("NL_API", "自然语言查询/聚合端点"),

    /**
     * 来自 /api/query/expression 或 /api/agg/expression 端点
     */
    EXPRESSION_API("EXPRESSION_API", "条件表达式查询/聚合端点"),

    /**
     * 来自 /api/query（countOnly）、/api/query/nl（countOnly）、/api/query/expression（countOnly）端点
     *
     * @since 1.6.6
     */
    COUNT_API("COUNT_API", "独立计数查询端点");

    private final String code;
    private final String description;

    SourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static SourceType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SourceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true: 有效, false: 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        SourceType[] types = values();
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
