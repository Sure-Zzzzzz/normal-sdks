package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.NLParserKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.SortIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 排序解析器
 * 负责从token列表中识别和解析排序表达式（如 "按创建时间降序"）
 *
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
public class SortParser {

    /**
     * 排序字段向前查找的最大距离
     */
    private static final int MAX_FIELD_LOOKAHEAD_DISTANCE = 5;

    /**
     * 解析排序表达式
     *
     * @param tokens token列表
     * @return 排序意图列表，如果没有排序则返回空列表
     */
    public List<SortIntent> parse(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new ArrayList<>();
        }

        List<SortIntent> sorts = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() == TokenType.SORT) {
                SortIntent sort = parseSort(tokens, i);
                if (sort != null) {
                    sorts.add(sort);
                }
            }
        }

        return sorts;
    }

    /**
     * 解析单个排序表达式
     *
     * @param tokens token列表
     * @param sortTokenIndex 排序token的索引
     * @return 排序意图，如果解析失败则返回null
     */
    private SortIntent parseSort(List<Token> tokens, int sortTokenIndex) {
        Token sortToken = tokens.get(sortTokenIndex);

        // 查找排序字段（向前查找，可能是多个连续的UNKNOWN token组成的字段名）
        String fieldHint = findSortField(tokens, sortTokenIndex);

        if (fieldHint != null && !fieldHint.isEmpty()) {
            return SortIntent.builder()
                    .fieldHint(fieldHint)
                    .order(sortToken.getSortOrder())
                    .build();
        }

        return null;
    }

    /**
     * 查找排序字段（向前查找）
     *
     * @param tokens token列表
     * @param sortTokenIndex 排序token的索引
     * @return 字段名，如果未找到则返回null
     */
    private String findSortField(List<Token> tokens, int sortTokenIndex) {
        List<String> fieldParts = new ArrayList<>();

        int startIndex = Math.max(0, sortTokenIndex - MAX_FIELD_LOOKAHEAD_DISTANCE);

        // 向前查找连续的字段候选token
        for (int j = sortTokenIndex - 1; j >= startIndex; j--) {
            Token prevToken = tokens.get(j);

            // 遇到明确的边界token，停止查找
            if (isBoundaryToken(prevToken)) {
                break;
            }

            if (prevToken.getType() == TokenType.UNKNOWN ||
                    prevToken.getType() == TokenType.FIELD_CANDIDATE) {
                // 遇到排序边界关键词，停止查找
                if (NLParserKeywords.isSortBoundary(prevToken.getText())) {
                    break;
                }
                fieldParts.add(0, prevToken.getText()); // 添加到开头保持顺序
            }
        }

        return fieldParts.isEmpty() ? null : String.join("", fieldParts);
    }

    /**
     * 判断是否为边界token（排序字段查找的终止点）
     *
     * @param token token
     * @return true表示是边界token
     */
    private boolean isBoundaryToken(Token token) {
        TokenType type = token.getType();
        return type == TokenType.OPERATOR ||
                type == TokenType.LOGIC ||
                type == TokenType.NUMBER ||
                type == TokenType.VALUE ||
                type == TokenType.DELIMITER ||
                type == TokenType.AGGREGATION;
    }
}
