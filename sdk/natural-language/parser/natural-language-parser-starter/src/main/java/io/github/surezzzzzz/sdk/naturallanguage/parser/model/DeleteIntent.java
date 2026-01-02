package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.*;

/**
 * 删除意图
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class DeleteIntent extends Intent {

    /**
     * 删除条件
     */
    private ConditionIntent condition;

    /**
     * 是否包含条件
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * 初始化
     */
    public DeleteIntent() {
        super(IntentType.DELETE);
    }

    /**
     * 构造函数
     */
    public DeleteIntent(ConditionIntent condition) {
        super(IntentType.DELETE);
        this.condition = condition;
    }
}
