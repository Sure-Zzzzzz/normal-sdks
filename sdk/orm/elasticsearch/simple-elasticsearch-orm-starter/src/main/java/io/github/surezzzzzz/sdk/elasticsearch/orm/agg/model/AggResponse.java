package io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 聚合响应
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggResponse {

    /**
     * 聚合结果
     * key: 聚合名称
     * value: 聚合结果（可能是数值、列表、嵌套对象等）
     */
    private Map<String, Object> aggregations;

    /**
     * 查询耗时（毫秒）
     */
    private Long took;

    /**
     * 初始化集合
     */
    public static class AggResponseBuilder {
        public AggResponseBuilder() {
            this.aggregations = new HashMap<>();
        }
    }
}
