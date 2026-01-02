package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy;

import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.List;

/**
 * Token拆分策略接口
 * <p>
 * 用于处理复合token的拆分（如"age>18"需要拆分为"age", ">", "18"）
 *
 * @author surezzzzzz
 */
public interface TokenSplitStrategy {

    /**
     * 检查是否可以处理该word
     *
     * @param word 待检查的词
     * @return true表示可以处理
     */
    boolean canHandle(String word);

    /**
     * 拆分word为多个token
     *
     * @param word 待拆分的词
     * @param position 位置
     * @param context 上下文（用于调用recognizeToken等方法）
     * @return 拆分后的token列表
     */
    List<Token> split(String word, int position, SplitContext context);
}
