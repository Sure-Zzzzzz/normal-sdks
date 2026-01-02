package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 插入意图
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class InsertIntent extends Intent {

    /**
     * 插入的数据
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * 是否包含数据
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    /**
     * 初始化
     */
    public InsertIntent() {
        super(IntentType.INSERT);
        this.data = new HashMap<>();
    }

    /**
     * 构造函数
     */
    public InsertIntent(Map<String, Object> data) {
        super(IntentType.INSERT);
        this.data = data != null ? data : new HashMap<>();
    }
}
