package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AggregationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合解析器
 * 负责从token列表中识别和解析聚合表达式（如 SUM、AVG、MAX、MIN、COUNT等）
 * <p>
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
public class AggregationParser {

    /**
     * 聚合字段向前查找的最大距离
     */
    private static final int MAX_FIELD_LOOKAHEAD_DISTANCE = 3;

    /**
     * 解析聚合表达式
     *
     * @param tokens token列表
     * @return 聚合意图列表，如果没有聚合则返回空列表
     */
    public List<AggregationIntent> parse(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new ArrayList<>();
        }

        List<AggregationIntent> aggregations = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() == TokenType.AGGREGATION) {
                AggregationIntent aggregation = parseAggregation(tokens, i);
                if (aggregation != null) {
                    aggregations.add(aggregation);
                }
            }
        }

        return aggregations;
    }

    /**
     * 解析单个聚合表达式
     *
     * @param tokens        token列表
     * @param aggTokenIndex 聚合token的索引
     * @return 聚合意图，如果解析失败则返回null
     */
    private AggregationIntent parseAggregation(List<Token> tokens, int aggTokenIndex) {
        Token aggToken = tokens.get(aggTokenIndex);

        AggregationIntent.AggregationIntentBuilder builder = AggregationIntent.builder();
        builder.type(aggToken.getAggType());
        builder.name(aggToken.getAggType().getCode());

        // 查找聚合的字段（下一个未知 token）
        String fieldHint = findAggregationField(tokens, aggTokenIndex);
        if (fieldHint != null) {
            builder.fieldHint(fieldHint);
        }

        return builder.build();
    }

    /**
     * 查找聚合字段
     *
     * @param tokens    token列表
     * @param fromIndex 起始索引
     * @return 字段名，如果未找到则返回null
     */
    private String findAggregationField(List<Token> tokens, int fromIndex) {
        int maxIndex = Math.min(tokens.size(), fromIndex + MAX_FIELD_LOOKAHEAD_DISTANCE + 1);

        for (int j = fromIndex + 1; j < maxIndex; j++) {
            Token nextToken = tokens.get(j);
            if (nextToken.getType() == TokenType.UNKNOWN ||
                    nextToken.getType() == TokenType.FIELD_CANDIDATE) {
                return nextToken.getText();
            }
        }

        return null;
    }
}
