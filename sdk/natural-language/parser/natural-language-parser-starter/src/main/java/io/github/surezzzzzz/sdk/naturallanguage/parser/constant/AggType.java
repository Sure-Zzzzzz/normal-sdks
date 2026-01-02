package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * 聚合类型
 *
 * @author surezzzzzz
 */
public enum AggType {

    /**
     * 平均值
     */
    AVG("avg", "平均值", true),

    /**
     * 求和
     */
    SUM("sum", "求和", true),

    /**
     * 最大值
     */
    MAX("max", "最大值", true),

    /**
     * 最小值
     */
    MIN("min", "最小值", true),

    /**
     * 计数
     */
    COUNT("count", "计数", true),

    /**
     * 基数（去重计数）
     */
    CARDINALITY("cardinality", "基数", true),

    /**
     * 统计（包含 count、min、max、avg、sum）
     */
    STATS("stats", "统计", true),

    /**
     * 分组聚合（桶聚合）
     */
    TERMS("terms", "分组", false),

    /**
     * 日期直方图
     */
    DATE_HISTOGRAM("date_histogram", "日期直方图", false),

    /**
     * 数值直方图
     */
    HISTOGRAM("histogram", "数值直方图", false),

    /**
     * 范围聚合
     */
    RANGE("range", "范围", false);

    private final String code;
    private final String description;
    private final boolean isMetric;

    AggType(String code, String description, boolean isMetric) {
        this.code = code;
        this.description = description;
        this.isMetric = isMetric;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为指标聚合
     */
    public boolean isMetric() {
        return isMetric;
    }

    /**
     * 是否为桶聚合
     */
    public boolean isBucket() {
        return !isMetric;
    }
}
