package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图抽象基类
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor(force = true)
public abstract class Intent {

    /**
     * 意图类型
     */
    private final IntentType type;

    /**
     * 索引/表名提示（可选）
     * 例如："查user索引" → indexHint = "user"
     */
    private String indexHint;

    /**
     * 构造函数
     */
    protected Intent(IntentType type) {
        this.type = type;
    }

    /**
     * 是否有索引提示
     */
    public boolean hasIndexHint() {
        return indexHint != null && !indexHint.trim().isEmpty();
    }
}
