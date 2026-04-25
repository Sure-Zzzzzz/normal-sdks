package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
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
     * 示例：状态 = "已完成" AND 金额 >= 100
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
}
