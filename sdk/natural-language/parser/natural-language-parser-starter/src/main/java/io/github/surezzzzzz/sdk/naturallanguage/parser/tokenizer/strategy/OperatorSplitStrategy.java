package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy;

import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 复合操作符拆分策略
 * <p>
 * 处理包含操作符的复合token（如 "age>18" 需要拆分为 "age", ">", "18"）
 *
 * @author surezzzzzz
 */
public class OperatorSplitStrategy implements TokenSplitStrategy {

    private static final Pattern OPERATOR_PATTERN = Pattern.compile(".*[><!=].*");
    private static final String OPERATOR_CHARS = "><=!";
    private static final String COMPOUND_OPERATORS = ">=<=!=";

    @Override
    public boolean canHandle(String word) {
        return OPERATOR_PATTERN.matcher(word).matches();
    }

    @Override
    public List<Token> split(String word, int position, SplitContext context) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int j = 0; j < word.length(); j++) {
            char c = word.charAt(j);
            if (OPERATOR_CHARS.indexOf(c) >= 0) {
                // 遇到操作符，先处理当前累积的内容
                if (current.length() > 0) {
                    Token t = context.recognizeToken(current.toString(), position);
                    if (!context.isStopWord(current.toString())) {
                        tokens.add(t);
                    }
                    current = new StringBuilder();
                }
                // 添加操作符（可能是复合操作符如>=）
                if (j + 1 < word.length() && COMPOUND_OPERATORS.contains(String.valueOf(c) + word.charAt(j + 1))) {
                    // 复合操作符
                    tokens.add(context.recognizeToken(String.valueOf(c) + word.charAt(j + 1), position));
                    j++; // 跳过下一个字符
                } else {
                    tokens.add(context.recognizeToken(String.valueOf(c), position));
                }
            } else {
                current.append(c);
            }
        }

        // 处理最后剩余的内容
        if (current.length() > 0) {
            Token t = context.recognizeToken(current.toString(), position);
            if (!context.isStopWord(current.toString())) {
                tokens.add(t);
            }
        }

        return tokens;
    }
}
