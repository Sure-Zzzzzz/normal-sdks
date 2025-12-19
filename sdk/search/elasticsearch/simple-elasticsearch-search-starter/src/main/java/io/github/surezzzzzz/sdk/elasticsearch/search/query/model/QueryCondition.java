package io.github.surezzzzzz.sdk.elasticsearch.search.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询条件
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryCondition {

    /**
     * 字段名
     */
    private String field;

    /**
     * 操作符
     */
    @JsonProperty("operator")
    private String op;

    /**
     * 值（单值）
     */
    private Object value;

    /**
     * 值列表（用于 IN、NOT_IN、BETWEEN）
     */
    private List<Object> values;

    /**
     * 逻辑运算符：and / or
     */
    private String logic;

    /**
     * 嵌套条件（用于组合查询）
     */
    private List<QueryCondition> conditions;

    /**
     * 获取操作符枚举
     */
    public QueryOperator getOperatorEnum() {
        return QueryOperator.fromString(op);
    }

    /**
     * 是否为逻辑组合条件
     */
    public boolean isLogicCondition() {
        return conditions != null && !conditions.isEmpty();
    }

    /**
     * 初始化集合
     */
    public static class QueryConditionBuilder {
        public QueryConditionBuilder() {
            this.conditions = new ArrayList<>();
        }
    }
}
