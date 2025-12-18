package io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model;

import io.github.surezzzzzz.sdk.elasticsearch.orm.query.model.QueryCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     * 初始化集合
     */
    public static class AggRequestBuilder {
        public AggRequestBuilder() {
            this.aggs = new ArrayList<>();
        }
    }
}
