package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 分析/聚合意图
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class AnalyticsIntent extends Intent {

    /**
     * 过滤条件（可选）
     */
    private ConditionIntent condition;

    /**
     * 聚合定义
     */
    @Builder.Default
    private List<AggregationIntent> aggregations = new ArrayList<>();

    /**
     * 是否包含过滤条件
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * 是否包含聚合
     */
    public boolean hasAggregation() {
        return aggregations != null && !aggregations.isEmpty();
    }

    /**
     * 初始化
     */
    public AnalyticsIntent() {
        super(IntentType.ANALYTICS);
        this.aggregations = new ArrayList<>();
    }

    /**
     * 构造函数
     */
    public AnalyticsIntent(ConditionIntent condition, List<AggregationIntent> aggregations) {
        super(IntentType.ANALYTICS);
        this.condition = condition;
        this.aggregations = aggregations != null ? aggregations : new ArrayList<>();
    }
}
