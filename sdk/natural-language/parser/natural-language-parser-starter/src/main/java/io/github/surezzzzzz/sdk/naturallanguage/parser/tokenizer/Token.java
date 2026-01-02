package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token（词元）
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    /**
     * Token 类型
     */
    private TokenType type;

    /**
     * Token 原始文本
     */
    private String text;

    /**
     * Token 值（解析后的值）
     */
    private Object value;

    /**
     * Token 在原文中的位置
     */
    private int position;

    /**
     * 操作符类型（如果是操作符）
     */
    private OperatorType operatorType;

    /**
     * 逻辑类型（如果是逻辑词）
     */
    private LogicType logicType;

    /**
     * 聚合类型（如果是聚合词）
     */
    private AggType aggType;

    /**
     * 排序类型（如果是排序词）
     */
    private SortOrder sortOrder;

    /**
     * 创建操作符 Token
     */
    public static Token operator(String text, OperatorType operatorType, int position) {
        return Token.builder()
                .type(TokenType.OPERATOR)
                .text(text)
                .operatorType(operatorType)
                .position(position)
                .build();
    }

    /**
     * 创建逻辑词 Token
     */
    public static Token logic(String text, LogicType logicType, int position) {
        return Token.builder()
                .type(TokenType.LOGIC)
                .text(text)
                .logicType(logicType)
                .position(position)
                .build();
    }

    /**
     * 创建聚合词 Token
     */
    public static Token aggregation(String text, AggType aggType, int position) {
        return Token.builder()
                .type(TokenType.AGGREGATION)
                .text(text)
                .aggType(aggType)
                .position(position)
                .build();
    }

    /**
     * 创建排序词 Token
     */
    public static Token sort(String text, SortOrder sortOrder, int position) {
        return Token.builder()
                .type(TokenType.SORT)
                .text(text)
                .sortOrder(sortOrder)
                .position(position)
                .build();
    }

    /**
     * 创建数值 Token
     */
    public static Token number(String text, Object value, int position) {
        return Token.builder()
                .type(TokenType.NUMBER)
                .text(text)
                .value(value)
                .position(position)
                .build();
    }

    /**
     * 创建字段候选 Token
     */
    public static Token fieldCandidate(String text, int position) {
        return Token.builder()
                .type(TokenType.FIELD_CANDIDATE)
                .text(text)
                .position(position)
                .build();
    }

    /**
     * 创建值 Token
     */
    public static Token value(String text, Object value, int position) {
        return Token.builder()
                .type(TokenType.VALUE)
                .text(text)
                .value(value != null ? value : text)
                .position(position)
                .build();
    }

    /**
     * 创建未知 Token
     */
    public static Token unknown(String text, int position) {
        return Token.builder()
                .type(TokenType.UNKNOWN)
                .text(text)
                .position(position)
                .build();
    }
}
