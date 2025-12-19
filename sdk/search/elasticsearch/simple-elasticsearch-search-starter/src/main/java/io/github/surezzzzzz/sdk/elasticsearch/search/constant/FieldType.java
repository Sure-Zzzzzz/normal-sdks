package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * Elasticsearch 字段类型枚举
 *
 * @author surezzzzzz
 */
public enum FieldType {

    /**
     * 文本类型（全文检索）
     */
    TEXT("text"),

    /**
     * 关键字类型（精确匹配、聚合、排序）
     */
    KEYWORD("keyword"),

    /**
     * 长整型
     */
    LONG("long"),

    /**
     * 整型
     */
    INTEGER("integer"),

    /**
     * 短整型
     */
    SHORT("short"),

    /**
     * 字节型
     */
    BYTE("byte"),

    /**
     * 双精度浮点型
     */
    DOUBLE("double"),

    /**
     * 单精度浮点型
     */
    FLOAT("float"),

    /**
     * 半精度浮点型
     */
    HALF_FLOAT("half_float"),

    /**
     * 缩放浮点型
     */
    SCALED_FLOAT("scaled_float"),

    /**
     * 日期类型
     */
    DATE("date"),

    /**
     * 布尔类型
     */
    BOOLEAN("boolean"),

    /**
     * 二进制类型
     */
    BINARY("binary"),

    /**
     * 整数范围类型
     */
    INTEGER_RANGE("integer_range"),

    /**
     * 浮点范围类型
     */
    FLOAT_RANGE("float_range"),

    /**
     * 长整型范围类型
     */
    LONG_RANGE("long_range"),

    /**
     * 双精度范围类型
     */
    DOUBLE_RANGE("double_range"),

    /**
     * 日期范围类型
     */
    DATE_RANGE("date_range"),

    /**
     * IP 地址类型
     */
    IP("ip"),

    /**
     * 嵌套对象类型
     */
    NESTED("nested"),

    /**
     * 对象类型
     */
    OBJECT("object"),

    /**
     * 地理坐标点类型
     */
    GEO_POINT("geo_point"),

    /**
     * 地理形状类型
     */
    GEO_SHAPE("geo_shape"),

    /**
     * 完成建议类型
     */
    COMPLETION("completion"),

    /**
     * 未知类型
     */
    UNKNOWN("unknown");

    private final String type;

    FieldType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * 根据字符串获取枚举值
     */
    public static FieldType fromString(String type) {
        if (type == null) {
            return UNKNOWN;
        }
        for (FieldType fieldType : values()) {
            if (fieldType.type.equalsIgnoreCase(type)) {
                return fieldType;
            }
        }
        return UNKNOWN;
    }

    /**
     * 是否为数值类型
     */
    public boolean isNumeric() {
        return this == LONG || this == INTEGER || this == SHORT || this == BYTE ||
                this == DOUBLE || this == FLOAT || this == HALF_FLOAT || this == SCALED_FLOAT;
    }

    /**
     * 是否为文本类型
     */
    public boolean isText() {
        return this == TEXT || this == KEYWORD;
    }

    /**
     * 是否可聚合
     */
    public boolean isAggregatable() {
        // TEXT 类型默认不可聚合（除非开启 fielddata）
        return this != TEXT && this != BINARY && this != NESTED && this != OBJECT;
    }

    /**
     * 是否可排序
     */
    public boolean isSortable() {
        // 同聚合
        return isAggregatable();
    }
}
