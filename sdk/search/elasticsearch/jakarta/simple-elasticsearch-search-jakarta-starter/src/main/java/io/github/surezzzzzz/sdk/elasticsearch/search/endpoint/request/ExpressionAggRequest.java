package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 表达式仅负责聚合前过滤，聚合定义与 composite 续页仍使用既有契约。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ExpressionAggRequest {
    private String index;
    private String expression;
    private QueryRequest.DateRange dateRange;
    private List<AggDefinition> aggs;
    private Map<String, Map<String, Object>> after;
    private TimeRangeEnd timeRangeEnd;
    private String timeZone;
    /**
     * 相对时间条件下 composite 遍历须传入并复用同一锚点。
     */
    private Instant timeRangeAnchor;
}
