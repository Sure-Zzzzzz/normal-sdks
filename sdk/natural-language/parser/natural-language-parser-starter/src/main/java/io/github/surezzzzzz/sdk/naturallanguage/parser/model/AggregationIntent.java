package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合意图
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregationIntent {

    /**
     * 聚合名称（别名）
     */
    private String name;

    /**
     * 聚合类型
     */
    private AggType type;

    /**
     * 字段提示（用户输入的）
     */
    private String fieldHint;

    /**
     * 分组字段提示（对于 TERMS 等桶聚合）
     */
    private String groupByFieldHint;

    /**
     * 区间大小（用于 HISTOGRAM, DATE_HISTOGRAM）
     */
    private String interval;

    /**
     * 桶大小（用于 TERMS）
     */
    private Integer size;

    /**
     * 嵌套聚合
     */
    @Builder.Default
    private List<AggregationIntent> children = new ArrayList<>();

    /**
     * 是否为桶聚合
     */
    public boolean isBucketAgg() {
        return type != null && type.isBucket();
    }

    /**
     * 是否为指标聚合
     */
    public boolean isMetricAgg() {
        return type != null && type.isMetric();
    }
}
