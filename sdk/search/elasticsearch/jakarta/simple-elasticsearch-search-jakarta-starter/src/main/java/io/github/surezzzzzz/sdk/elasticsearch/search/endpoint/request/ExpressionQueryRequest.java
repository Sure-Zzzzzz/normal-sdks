package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * 表达式查询复用结构化分页、投影、日期范围与 countOnly，不接收原始 ES DSL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ExpressionQueryRequest {
    private String index;
    private String expression;
    private QueryRequest.DateRange dateRange;
    private PaginationInfo pagination;
    private List<String> fields;
    private QueryRequest.CollapseField collapse;
    private Boolean countOnly;
    private TimeRangeEnd timeRangeEnd;
    private String timeZone;
    /**
     * 相对时间的固定锚点；非 offset 遍历含时间关键字时必须显式传入并原样续传。
     */
    private Instant timeRangeAnchor;
}
