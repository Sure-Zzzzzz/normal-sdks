package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy;

import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 分隔符拆分策略
 * <p>
 * 处理包含分隔符的复合token（如 "18,30" 需要拆分为 "18", ",", "30"）
 *
 * @author surezzzzzz
 */
public class DelimiterSplitStrategy implements TokenSplitStrategy {

    private static final Pattern DELIMITER_PATTERN = Pattern.compile(".*[,，、;；].*");
    private static final String DELIMITER_CHARS = ",，、;；";

    @Override
    public boolean canHandle(String word) {
        return DELIMITER_PATTERN.matcher(word).matches();
    }

    @Override
    public List<Token> split(String word, int position, SplitContext context) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (char c : word.toCharArray()) {
            if (DELIMITER_CHARS.indexOf(c) >= 0) {
                // 遇到分隔符，先处理当前累积的内容
                if (current.length() > 0) {
                    Token t = context.recognizeToken(current.toString(), position);
                    if (!context.isStopWord(current.toString())) {
                        tokens.add(t);
                    }
                    current = new StringBuilder();
                }
                // 添加分隔符token
                tokens.add(context.recognizeToken(String.valueOf(c), position));
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
