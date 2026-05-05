package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.SortOrder;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * 聚合名称提示
     */
    private String nameHint;

    /**
     * 聚合类型
     */
    private AggType type;

    /**
     * 字段提示
     */
    private String fieldHint;

    /**
     * 分组字段提示（用于 terms 等桶聚合）
     */
    private String groupByFieldHint;

    /**
     * 桶大小（用于 terms 聚合）
     */
    private Integer size;

    /**
     * 时间间隔（用于 date_histogram）
     */
    private String interval;

    /**
     * 范围列表（用于 range 聚合）
     */
    private List<AggRangeHint> ranges;

    /**
     * 百分位列表（用于 percentiles 聚合）
     */
    private List<Double> percents;

    /**
     * 百分位排名值列表（用于 percentile_ranks 聚合）
     */
    private List<Double> percentileValues;

    /**
     * 过滤条件（用于 filter 聚合）
     */
    private ConditionIntent filterCondition;

    /**
     * 多命名过滤器（用于 filters 聚合）
     * key：bucket 名称，value：对应的过滤条件
     */
    private Map<String, ConditionIntent> namedFilters;

    /**
     * 是否使用 composite 聚合
     */
    private Boolean useComposite;

    /**
     * composite 聚合排序方向
     */
    private SortOrder compositeOrder;

    /**
     * 嵌套子聚合
     */
    @Builder.Default
    private List<AggregationIntent> subAggs = new ArrayList<>();

    /**
     * Pipeline 聚合列表
     */
    @Builder.Default
    private List<PipelineAggIntent> pipelineAggs = new ArrayList<>();

    /**
     * 获取字段提示（优先使用 groupByFieldHint）
     *
     * @return 字段提示
     */
    public String getFieldHint() {
        if (groupByFieldHint != null) {
            return groupByFieldHint;
        }
        return fieldHint;
    }
}
