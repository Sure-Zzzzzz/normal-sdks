package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

import lombok.Getter;

/**
 * 聚合类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum AggType {

    // ==================== 指标聚合 ====================

    /**
     * 平均值
     */
    AVG("avg", "平均值", true),

    /**
     * 求和
     */
    SUM("sum", "求和", true),

    /**
     * 最小值
     */
    MIN("min", "最小值", true),

    /**
     * 最大值
     */
    MAX("max", "最大值", true),

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
     * 扩展统计
     */
    EXTENDED_STATS("extended_stats", "扩展统计", true),

    /**
     * 百分位数
     */
    PERCENTILES("percentiles", "百分位数", true),

    /**
     * 百分位排名
     */
    PERCENTILE_RANKS("percentile_ranks", "百分位排名", true),

    // ==================== 桶聚合 ====================

    /**
     * 分组聚合
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
    RANGE("range", "范围", false),

    /**
     * 日期范围聚合
     */
    DATE_RANGE("date_range", "日期范围", false),

    /**
     * IP 范围聚合
     */
    IP_RANGE("ip_range", "IP 范围", false),

    /**
     * 单过滤器聚合
     */
    FILTER("filter", "单过滤器", false),

    /**
     * 多过滤器聚合
     */
    FILTERS("filters", "多过滤器", false),

    /**
     * 缺失值聚合
     */
    MISSING("missing", "缺失值", false),

    // ==================== Pipeline 聚合 ====================

    /**
     * 桶排序
     */
    BUCKET_SORT("bucket_sort", "桶排序", true),

    /**
     * 桶选择
     */
    BUCKET_SELECTOR("bucket_selector", "桶选择", true);

    private final String code;
    private final String description;
    private final boolean metric;

    AggType(String code, String description, boolean metric) {
        this.code = code;
        this.description = description;
        this.metric = metric;
    }

    /**
     * 是否为桶聚合
     *
     * @return true 桶聚合，false 指标聚合
     */
    public boolean isBucket() {
        return !metric;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static AggType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AggType type : values()) {
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
     * @return true 有效，false 无效
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
        AggType[] types = values();
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
