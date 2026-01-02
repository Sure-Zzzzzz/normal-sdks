package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 更新意图
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class UpdateIntent extends Intent {

    /**
     * 更新条件
     */
    private ConditionIntent condition;

    /**
     * 更新的字段和值
     */
    @Builder.Default
    private Map<String, Object> updates = new HashMap<>();

    /**
     * 是否包含条件
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * 是否包含更新字段
     */
    public boolean hasUpdates() {
        return updates != null && !updates.isEmpty();
    }

    /**
     * 初始化
     */
    public UpdateIntent() {
        super(IntentType.UPDATE);
        this.updates = new HashMap<>();
    }

    /**
     * 构造函数
     */
    public UpdateIntent(ConditionIntent condition, Map<String, Object> updates) {
        super(IntentType.UPDATE);
        this.condition = condition;
        this.updates = updates != null ? updates : new HashMap<>();
    }
}
