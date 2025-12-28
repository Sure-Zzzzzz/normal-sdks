package io.github.surezzzzzz.sdk.sensitive.keyword.nlp;

import io.github.surezzzzzz.sdk.sensitive.keyword.extractor.MetaInfo;

/**
 * NLP Provider Interface
 *
 * @author surezzzzzz
 */
public interface NLPProvider {

    /**
     * 提取元信息
     *
     * @param text 文本
     * @return 元信息
     */
    MetaInfo extract(String text);

    /**
     * 批量提取元信息
     *
     * @param texts 文本列表
     * @return 元信息列表
     */
    default java.util.List<MetaInfo> batchExtract(java.util.List<String> texts) {
        return texts.stream()
                .map(this::extract)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 判断是否可用
     *
     * @return true表示可用
     */
    boolean isAvailable();

    /**
     * 获取提供者名称
     *
     * @return 提供者名称
     */
    String getProviderName();
}
