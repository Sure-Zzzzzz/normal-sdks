package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 表达式聚合请求
 * 使用条件表达式字符串作为聚合过滤条件，其余字段与 AggRequest 一致。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpressionAggRequest {

    /**
     * 索引别名或名称（必填）
     */
    private String index;

    /**
     * 条件表达式字符串（必填）
     * 示例：事件类型 = "mock-value" AND 风险等级 >= 10
     */
    private String expression;

    /**
     * 聚合定义列表（必填）
     */
    private List<AggDefinition> aggs;

    /**
     * composite 聚合翻页游标（可选）
     * key：聚合名称，value：上一页的 afterKey
     */
    private Map<String, Map<String, Object>> after;

    /**
     * 表达式时间关键字的截止模式（可选，默认 NOW）
     */
    private TimeRangeEnd timeRangeEnd;

    /**
     * 表达式时间关键字使用的 IANA 时区（可选，默认 JVM 系统时区）
     */
    private String timeZone;
}
