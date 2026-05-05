package io.github.surezzzzzz.sdk.naturallanguage.parser.support;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.Collections;
import java.util.List;

/**
 * Token 辅助工具
 *
 * @author surezzzzzz
 */
public final class TokenHelper {

    private TokenHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断 token 是否为边界 token（不应被收集为字段名的一部分）
     */
    public static boolean isBoundaryToken(Token token) {
        if (token == null) return false;
        TokenType type = token.getType();
        return type == TokenType.OPERATOR
                || type == TokenType.LOGIC
                || type == TokenType.NUMBER
                || type == TokenType.VALUE
                || type == TokenType.DELIMITER
                || type == TokenType.AGGREGATION
                || type == TokenType.COLLAPSE
                || type == TokenType.SORT
                || type == TokenType.PAGINATION;
    }

    /**
     * 按索引从高到低删除 token（从后往前删，避免索引偏移）
     */
    public static void removeByIndices(List<Token> tokens, List<Integer> indices) {
        if (indices == null || indices.isEmpty()) return;
        Collections.sort(indices, Collections.reverseOrder());
        for (int index : indices) {
            if (index >= 0 && index < tokens.size()) {
                tokens.remove(index);
            }
        }
    }
}
