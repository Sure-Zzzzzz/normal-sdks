package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer;

import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;

import java.util.List;

/**
 * 自然语言分词器接口
 *
 * @author surezzzzzz
 */
public interface NLTokenizer {

    /**
     * 分词并识别 Token（使用关键字注册表）
     *
     * @param text            原始文本
     * @param keywordRegistry 关键字注册表
     * @return token 列表
     */
    List<Token> tokenize(String text, KeywordRegistry keywordRegistry);
}
