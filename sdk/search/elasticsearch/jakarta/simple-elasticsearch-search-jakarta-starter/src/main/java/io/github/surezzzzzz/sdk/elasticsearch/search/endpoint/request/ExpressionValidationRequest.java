package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.*;

import java.time.Instant;

/**
 * 校验语法及当前目标的字段能力；不执行查询、不在 URL 中传表达式原文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ExpressionValidationRequest {
    private String index;
    private String expression;
    private QueryRequest.DateRange dateRange;
    private TimeRangeEnd timeRangeEnd;
    private String timeZone;
    private Instant timeRangeAnchor;
}
