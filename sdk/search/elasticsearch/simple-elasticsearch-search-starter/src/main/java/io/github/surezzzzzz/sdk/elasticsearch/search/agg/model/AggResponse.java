package io.github.surezzzzzz.sdk.elasticsearch.search.agg.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
     * 原始聚合响应（仅 ES 6.x 聚合场景 + 配置启用时返回）
     * <p>
     * 包含未解析的原始聚合数据（如 {"avg_price": {"value": 6439.0}}），
     * 让用户可以对比 aggregations 字段（解析后的 {"avg_price": 6439.0}）和原始数据，
     * 自主选择使用哪个格式
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> rawResponse;

    /**
     * 初始化集合
     */
    public static class AggResponseBuilder {
        public AggResponseBuilder() {
            this.aggregations = new HashMap<>();
        }
    }
}
