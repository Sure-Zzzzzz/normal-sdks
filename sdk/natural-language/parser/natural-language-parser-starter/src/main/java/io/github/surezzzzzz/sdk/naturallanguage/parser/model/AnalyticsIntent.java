package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合分析意图
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
public class AnalyticsIntent extends Intent {

    /**
     * 过滤条件
     */
    private ConditionIntent condition;

    /**
     * 聚合定义列表
     */
    @Builder.Default
    private List<AggregationIntent> aggregations = new ArrayList<>();

    public AnalyticsIntent() {
        super(IntentType.ANALYTICS);
    }

    /**
     * 是否有过滤条件
     *
     * @return true 有，false 无
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * 是否有聚合
     *
     * @return true 有，false 无
     */
    public boolean hasAggregation() {
        return aggregations != null && !aggregations.isEmpty();
    }
}
