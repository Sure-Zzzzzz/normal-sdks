package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

/**
 * 拆分上下文
 * <p>
 * 提供策略需要的公共方法
 *
 * @author surezzzzzz
 */
public interface SplitContext {

    /**
     * 识别token类型
     *
     * @param word 词
     * @param position 位置
     * @return token
     */
    Token recognizeToken(String word, int position);

    /**
     * 判断是否为停用词
     *
     * @param word 词
     * @return true表示是停用词
     */
    boolean isStopWord(String word);
}
