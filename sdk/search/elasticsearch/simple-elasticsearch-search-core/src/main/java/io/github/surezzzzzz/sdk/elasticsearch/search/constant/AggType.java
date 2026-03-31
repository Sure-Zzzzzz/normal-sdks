package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 聚合类型枚举
 *
 * @author surezzzzzz
 */
public enum AggType {

    // ========== Metrics 聚合（指标聚合）==========

    /**
     * 求和
     */
    SUM("sum", "求和"),

    /**
     * 平均值
     */
    AVG("avg", "平均值"),

    /**
     * 最小值
     */
    MIN("min", "最小值"),

    /**
     * 最大值
     */
    MAX("max", "最大值"),

    /**
     * 计数
     */
    COUNT("count", "计数"),

    /**
     * 去重计数
     */
    CARDINALITY("cardinality", "去重计数"),

    /**
     * 统计（包含 count, min, max, avg, sum）
     */
    STATS("stats", "统计"),

    /**
     * 扩展统计（额外包含方差、标准差等）
     */
    EXTENDED_STATS("extended_stats", "扩展统计"),

    /**
     * 百分位
     */
    PERCENTILES("percentiles", "百分位"),

    /**
     * 百分位排名
     */
    PERCENTILE_RANKS("percentile_ranks", "百分位排名"),

    // ========== Bucket 聚合（桶聚合）==========

    /**
     * 分组聚合（类似 SQL GROUP BY）
     */
    TERMS("terms", "分组聚合"),

    /**
     * 日期直方图
     */
    DATE_HISTOGRAM("date_histogram", "日期直方图"),

    /**
     * 数值直方图
     */
    HISTOGRAM("histogram", "数值直方图"),

    /**
     * 范围聚合
     */
    RANGE("range", "范围聚合"),

    /**
     * 日期范围聚合
     */
    DATE_RANGE("date_range", "日期范围聚合"),

    /**
     * IP 范围聚合
     */
    IP_RANGE("ip_range", "IP范围聚合"),

    /**
     * 过滤器聚合
     */
    FILTER("filter", "过滤器聚合"),

    /**
     * 多过滤器聚合
     */
    FILTERS("filters", "多过滤器聚合"),

    /**
     * 缺失值聚合
     */
    MISSING("missing", "缺失值聚合");

    private final String type;
    private final String description;

    AggType(String type, String description) {
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
    public static AggType fromString(String type) {
        if (type == null) {
            return null;
        }
        for (AggType aggType : values()) {
            if (aggType.type.equalsIgnoreCase(type)) {
                return aggType;
            }
        }
        throw new IllegalArgumentException("不支持的聚合类型: " + type);
    }

    /**
     * 是否为 Metrics 聚合
     */
    public boolean isMetrics() {
        return this == SUM || this == AVG || this == MIN || this == MAX ||
                this == COUNT || this == CARDINALITY || this == STATS ||
                this == EXTENDED_STATS || this == PERCENTILES || this == PERCENTILE_RANKS;
    }

    /**
     * 是否为 Bucket 聚合
     */
    public boolean isBucket() {
        return !isMetrics();
    }

    /**
     * 是否支持嵌套聚合
     */
    public boolean supportsSubAggregation() {
        return isBucket();
    }
}
