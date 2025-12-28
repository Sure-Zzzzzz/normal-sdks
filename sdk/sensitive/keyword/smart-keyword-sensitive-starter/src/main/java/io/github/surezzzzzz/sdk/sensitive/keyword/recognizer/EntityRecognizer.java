package io.github.surezzzzzz.sdk.sensitive.keyword.recognizer;

import java.util.List;

/**
 * Entity Recognizer Interface
 * 实体识别器接口 - 用于识别文本中的敏感实体（如组织机构名称）
 *
 * @author surezzzzzz
 */
public interface EntityRecognizer {

    /**
     * 识别文本中的实体
     *
     * @param text 文本
     * @return 识别结果列表
     */
    List<RecognizeResult> recognize(String text);

    /**
     * 判断识别器是否可用
     *
     * @return true表示可用
     */
    default boolean isAvailable() {
        return true;
    }
}
