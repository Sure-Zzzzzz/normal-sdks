package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 意图抽象基类
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public abstract class Intent {

    /**
     * 意图类型
     */
    private IntentType type;

    /**
     * 索引/表名提示（从自然语言中提取）
     */
    private String indexHint;

    /**
     * 扩展数据 — 由自定义 NLParserPlugin 写入
     * IntentTranslator 可通过 getExt() 读取，实现自定义 Intent 信息的传递
     */
    private Map<String, Object> ext;

    /**
     * 是否有索引提示
     *
     * @return true 有，false 无
     */
    public boolean hasIndexHint() {
        return indexHint != null && !indexHint.trim().isEmpty();
    }

    protected Intent(IntentType type) {
        this.type = type;
    }

    /**
     * 无参构造器（供 Lombok @NoArgsConstructor 使用）
     * type 由 NLParser 组装时通过 setType() 设置
     */
    public Intent() {
    }
}
