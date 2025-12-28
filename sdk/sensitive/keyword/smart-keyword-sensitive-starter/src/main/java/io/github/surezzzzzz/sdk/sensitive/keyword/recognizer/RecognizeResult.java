package io.github.surezzzzzz.sdk.sensitive.keyword.recognizer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Entity Recognition Result
 * 实体识别结果
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecognizeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 识别到的实体名称
     */
    private String entity;

    /**
     * 开始位置
     */
    private int startIndex;

    /**
     * 结束位置
     */
    private int endIndex;

    /**
     * 置信度（0.0-1.0）
     */
    private double confidence;

    /**
     * 识别来源（RULE / NLP / BRAND / INDUSTRY）
     */
    private String source;

    @Override
    public String toString() {
        return String.format("RecognizeResult{entity='%s', range=[%d,%d), confidence=%.2f, source=%s}",
                entity, startIndex, endIndex, confidence, source);
    }
}
