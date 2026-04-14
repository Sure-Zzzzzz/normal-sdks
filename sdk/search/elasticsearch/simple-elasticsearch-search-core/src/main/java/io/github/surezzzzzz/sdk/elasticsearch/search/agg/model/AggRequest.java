package io.github.surezzzzzz.sdk.elasticsearch.search.agg.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聚合请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AggRequest {

    /**
     * 索引别名
     */
    private String index;

    /**
     * 过滤条件（可选）
     */
    private QueryCondition query;

    /**
     * 聚合定义列表
     */
    private List<AggDefinition> aggs;

    /**
     * composite 聚合的翻页游标
     * key：聚合名称（对应 AggDefinition.name）
     * value：上一页响应中返回的 afterKey
     * <p>
     * 支持同一请求中多个 composite 聚合各自独立翻页
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Map<String, Object>> after;

    /**
     * 初始化集合
     */
    public static class AggRequestBuilder {
        public AggRequestBuilder() {
            this.aggs = new ArrayList<>();
        }
    }
}
