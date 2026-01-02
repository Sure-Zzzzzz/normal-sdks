package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy;

import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.AggKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.LogicKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.OperatorKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.SortKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 逻辑关键词拆分策略
 * <p>
 * 处理包含逻辑关键词的token（如 "张或" 需要拆分为 "张", "或"）
 *
 * @author surezzzzzz
 */
public class LogicKeywordSplitStrategy implements TokenSplitStrategy {

    // 按长度从长到短排列，避免"并且"被识别为"并"+"且"
    private static final String[] LOGIC_KEYWORDS = {
            "并且", "或者", "要么", "或是", "而且", "同时",
            "and", "AND", "or", "OR",
            "&&", "||",
            "和", "且", "并", "或"
    };

    @Override
    public boolean canHandle(String word) {
        // 如果整个词就是逻辑关键词，不需要拆分
        if (LogicKeywords.fromKeyword(word) != null) {
            return false;
        }

        // 如果整个词是其他类型的关键词（操作符、聚合、排序），不拆分
        if (OperatorKeywords.fromKeyword(word) != null ||
                AggKeywords.fromKeyword(word) != null ||
                SortKeywords.fromKeyword(word) != null) {
            return false;
        }

        // 检查是否包含逻辑关键词
        for (String keyword : LOGIC_KEYWORDS) {
            if (word.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<Token> split(String word, int position, SplitContext context) {
        List<Token> result = new ArrayList<>();

        // 检查是否包含逻辑关键词（按长度从长到短检查）
        for (String keyword : LOGIC_KEYWORDS) {
            int index = word.indexOf(keyword);
            if (index >= 0) {
                // 找到逻辑关键词，进行拆分
                String before = word.substring(0, index);
                String after = word.substring(index + keyword.length());

                // 添加前面的部分
                if (!before.isEmpty()) {
                    Token t = context.recognizeToken(before, position);
                    if (!context.isStopWord(before)) {
                        result.add(t);
                    }
                }

                // 添加逻辑关键词
                result.add(context.recognizeToken(keyword, position));

                // 添加后面的部分
                if (!after.isEmpty()) {
                    Token t = context.recognizeToken(after, position);
                    if (!context.isStopWord(after)) {
                        result.add(t);
                    }
                }

                return result;
            }
        }

        // 没有找到逻辑关键词（理论上不应该到这里）
        result.add(context.recognizeToken(word, position));
        return result;
    }
}
