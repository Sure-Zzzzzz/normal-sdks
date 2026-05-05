package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询条件意图
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionIntent {

    /**
     * 字段提示
     */
    private String fieldHint;

    /**
     * 操作符
     */
    private OperatorType operator;

    /**
     * 值
     */
    private Object value;

    /**
     * 多值列表（用于 IN、NOT_IN、BETWEEN）
     */
    private List<Object> values;

    /**
     * 逻辑类型（AND / OR / NOT）
     */
    private LogicType logic;

    /**
     * 子条件列表（用于逻辑组合）
     */
    @Builder.Default
    private List<ConditionIntent> children = new ArrayList<>();

    /**
     * 是否为逻辑组合条件
     *
     * @return true 逻辑组合，false 简单条件
     */
    public boolean isLogicCondition() {
        return logic != null;
    }

    /**
     * 是否为简单条件
     *
     * @return true 简单条件，false 逻辑组合
     */
    public boolean isSimpleCondition() {
        return logic == null;
    }

    /**
     * 获取操作符代码
     *
     * @return 操作符代码
     */
    public String getOperatorCode() {
        return operator != null ? operator.getCode() : null;
    }
}
