package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 分页类型枚举
 *
 * @author surezzzzzz
 */
public enum PaginationType {

    /**
     * 偏移分页（from + size）
     * 适用于浅分页场景，性能较好，但有深度限制（默认最大 10000）
     */
    OFFSET("offset", "偏移分页"),

    /**
     * 游标分页（search_after）
     * 适用于深分页场景，无深度限制，但必须有排序字段
     */
    SEARCH_AFTER("search_after", "游标分页");

    private final String type;
    private final String description;

    PaginationType(String type, String description) {
        this.type = type;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串获取枚举值
     */
    public static PaginationType fromString(String type) {
        if (type == null) {
            return OFFSET; // 默认使用 offset
        }
        for (PaginationType pt : values()) {
            if (pt.type.equalsIgnoreCase(type)) {
                return pt;
            }
        }
        throw new IllegalArgumentException("不支持的分页类型: " + type);
    }
}
