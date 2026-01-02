package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件意图（抽象的查询条件，不依赖具体数据源）
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionIntent {

    /**
     * 字段提示（用户输入的，可能是中文、拼音、英文）
     * 例如："年龄", "nianling", "age"
     */
    private String fieldHint;

    /**
     * 操作符
     */
    private OperatorType operator;

    /**
     * 值（单值）
     */
    private Object value;

    /**
     * 值列表（用于 IN、NOT_IN、BETWEEN）
     */
    @Builder.Default
    private List<Object> values = new ArrayList<>();

    /**
     * 逻辑运算符
     */
    private LogicType logic;

    /**
     * 嵌套条件
     */
    @Builder.Default
    private List<ConditionIntent> children = new ArrayList<>();

    /**
     * 是否为逻辑组合条件
     */
    public boolean isLogicCondition() {
        return children != null && !children.isEmpty();
    }
}
