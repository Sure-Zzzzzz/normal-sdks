package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.AggKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.NLParserKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.ConditionIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件解析器（核心）
 * 使用状态机解析自然语言条件表达式
 * <p>
 * 状态机：
 * EXPECT_FIELD → EXPECT_OPERATOR → EXPECT_VALUE → EXPECT_LOGIC_OR_END
 * <p>
 * 关键功能：
 * 1. 多值操作符支持（IN, BETWEEN）
 * 2. 单值操作符的OR逻辑支持（LIKE）
 * 3. 逗号消歧：区分值分隔符 vs 子句分隔符
 * 4. 隐式AND逻辑
 * 5. 连续操作符（同一字段多个条件）
 * <p>
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
public class ConditionParser {

    /**
     * 解析状态
     */
    private enum ParseState {
        EXPECT_FIELD,           // 期待字段名
        EXPECT_OPERATOR,        // 期待操作符
        EXPECT_VALUE,           // 期待值
        EXPECT_LOGIC_OR_END     // 期待逻辑词或结束
    }

    /**
     * 解析条件表达式
     *
     * @param tokens token列表
     * @return 条件意图，如果没有条件则返回null
     */
    public ConditionIntent parse(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        ConditionIntentBuilder rootBuilder = new ConditionIntentBuilder();
        ConditionIntentBuilder currentBuilder = rootBuilder;

        ParseState state = ParseState.EXPECT_FIELD;
        String currentField = null;
        String lastField = null;  // 保存上一个字段名，用于连续操作符继承
        OperatorType currentOperator = null;
        List<Object> currentValues = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // 跳过聚合、排序相关的 token
            if (token.getType() == TokenType.AGGREGATION || token.getType() == TokenType.SORT) {
                continue;
            }

            switch (state) {
                case EXPECT_FIELD:
                    if (token.getType() == TokenType.UNKNOWN || token.getType() == TokenType.FIELD_CANDIDATE) {
                        // 合并连续的字段tokens（如"目标IP"会被分成"目标"和"IP"两个tokens）
                        StringBuilder fieldBuilder = new StringBuilder(token.getText());
                        int lookAhead = i + 1;
                        while (lookAhead < tokens.size()) {
                            Token nextToken = tokens.get(lookAhead);
                            if (nextToken.getType() == TokenType.UNKNOWN ||
                                    nextToken.getType() == TokenType.FIELD_CANDIDATE) {
                                fieldBuilder.append(nextToken.getText());
                                lookAhead++;
                            } else {
                                break;
                            }
                        }
                        currentField = fieldBuilder.toString();
                        // 跳过已经合并的tokens
                        i = lookAhead - 1;
                        state = ParseState.EXPECT_OPERATOR;
                    }
                    break;

                case EXPECT_OPERATOR:
                    if (token.getType() == TokenType.OPERATOR) {
                        currentOperator = token.getOperatorType();
                        // 检查操作符是否需要值
                        if (currentOperator.needsValue()) {
                            state = ParseState.EXPECT_VALUE;
                        } else {
                            // EXISTS, NOT_EXISTS 等不需要值
                            currentBuilder.addCondition(currentField, currentOperator, null, null);
                            lastField = currentField;
                            currentField = null;
                            currentOperator = null;
                            state = ParseState.EXPECT_LOGIC_OR_END;
                        }
                    }
                    // 注意：移除了对UNKNOWN token的拼写检测，因为太激进
                    // UNKNOWN可能是分页关键词、范围关键词等，不应该在这里检测
                    break;

                case EXPECT_VALUE:
                    // 处理分隔符
                    if (token.getType() == TokenType.DELIMITER) {
                        // 检查分隔符后面是否是新的子句（字段+操作符模式）
                        boolean isNewClause = isClauseBoundary(tokens, i);

                        // 只有在收集多值操作符的值时，且不是子句边界，才跳过分隔符继续收集
                        if (currentOperator != null && currentOperator.needsMultipleValues() &&
                                !currentValues.isEmpty() && !isNewClause) {
                            continue; // 保持在EXPECT_VALUE状态，继续收集下一个值
                        }
                        // 对于单值操作符，分隔符表示子句结束，不应该出现在这里
                        // 如果出现，说明缺少值，忽略该分隔符并期待值
                        if (currentValues.isEmpty()) {
                            continue;
                        }
                        // 如果已经有值了，或者检测到子句边界，完成条件并转到EXPECT_LOGIC_OR_END
                        if (currentOperator.needsMultipleValues()) {
                            currentBuilder.addCondition(currentField, currentOperator, null, currentValues);
                        } else if (currentValues.size() == 1) {
                            currentBuilder.addCondition(currentField, currentOperator, currentValues.get(0), null);
                        } else {
                            currentBuilder.addCondition(currentField, currentOperator, null, currentValues);
                        }
                        lastField = currentField;
                        currentField = null;
                        currentOperator = null;
                        currentValues = new ArrayList<>();
                        state = ParseState.EXPECT_LOGIC_OR_END;
                        // 不要consume这个delimiter，让EXPECT_LOGIC_OR_END处理它
                        i--;
                        break;
                    }

                    // 对于单值操作符（LIKE等），如果遇到OR逻辑词，表示要创建多个条件
                    if (token.getType() == TokenType.LOGIC &&
                            token.getLogicType() == LogicType.OR &&
                            !currentOperator.needsMultipleValues()) {
                        // 先完成当前条件
                        if (!currentValues.isEmpty()) {
                            currentBuilder.addCondition(currentField, currentOperator, currentValues.get(0), null);
                            currentBuilder.setLogic(LogicType.OR);
                        }
                        // 继续收集下一个值（保持字段和操作符不变）
                        currentValues = new ArrayList<>();
                        continue;
                    }

                    if (token.getType() == TokenType.NUMBER ||
                            token.getType() == TokenType.VALUE ||
                            token.getType() == TokenType.UNKNOWN) {

                        Object value = token.getValue() != null ? token.getValue() : token.getText();
                        currentValues.add(value);

                        // 检查是否继续收集值
                        boolean hasMore = shouldContinueCollectingValues(tokens, i, currentOperator);

                        if (hasMore) {
                            continue; // 继续收集下一个值
                        }

                        // 完成值收集，构建条件
                        buildConditionAndTransitionState(currentBuilder, currentField, currentOperator, currentValues);

                        // 重置状态，但保存字段名以便后续连续操作符继承
                        lastField = currentField;
                        currentField = null;
                        currentOperator = null;
                        currentValues = new ArrayList<>();
                        state = ParseState.EXPECT_LOGIC_OR_END;
                    }
                    break;

                case EXPECT_LOGIC_OR_END:
                    if (token.getType() == TokenType.LOGIC) {
                        LogicType logic = token.getLogicType();
                        currentBuilder.setLogic(logic);
                        lastField = null;  // 遇到逻辑词，清空lastField
                        state = ParseState.EXPECT_FIELD;
                    } else if (token.getType() == TokenType.DELIMITER) {
                        // 逗号在这里作为子句分隔符，等同于AND
                        if (currentBuilder.hasConditions()) {
                            currentBuilder.setLogic(LogicType.AND);
                        }
                        lastField = null;  // 遇到分隔符，清空lastField
                        state = ParseState.EXPECT_FIELD;
                    } else if (token.getType() == TokenType.OPERATOR) {
                        // 遇到操作符，说明是隐式的AND + 相同字段的新条件
                        // 例如："年龄大于等于18小于60" → "年龄>=18 AND 年龄<60"
                        if (currentBuilder.hasConditions()) {
                            currentBuilder.setLogic(LogicType.AND);
                        }
                        currentOperator = token.getOperatorType();
                        // 继承上一个字段名
                        currentField = lastField;
                        if (currentOperator.needsValue()) {
                            state = ParseState.EXPECT_VALUE;
                        } else {
                            currentBuilder.addCondition(currentField, currentOperator, null, null);
                            lastField = currentField;
                            currentField = null;
                            currentOperator = null;
                            state = ParseState.EXPECT_LOGIC_OR_END;
                        }
                    } else if (token.getType() == TokenType.UNKNOWN || token.getType() == TokenType.FIELD_CANDIDATE) {
                        // 新字段开始，可能省略了逻辑词，默认用 AND
                        if (currentBuilder.hasConditions()) {
                            currentBuilder.setLogic(LogicType.AND);
                        }
                        lastField = null;  // 新字段开始，清空lastField
                        currentField = token.getText();
                        state = ParseState.EXPECT_OPERATOR;
                    }
                    break;
            }
        }

        // 解析完成后检查状态是否完整
        validateParseState(state, currentField, currentOperator, currentValues, tokens);

        return rootBuilder.build();
    }

    /**
     * 验证解析状态是否完整
     * 如果状态不完整，抛出详细的异常
     */
    private void validateParseState(ParseState state,
                                    String currentField,
                                    OperatorType currentOperator,
                                    List<Object> currentValues,
                                    List<Token> tokens) {
        // 只检测EXPECT_VALUE状态的错误（操作符后缺少值）
        // EXPECT_OPERATOR状态可能是因为遇到了其他parser要处理的token（如AGGREGATION、SORT等）
        // 所以不一定是错误，暂不检测
        if (state == ParseState.EXPECT_VALUE) {
            // 操作符后缺少值
            if (currentOperator != null) {
                String operatorText = getOperatorText(currentOperator);
                throw NLParseException.missingValue(null, -1, operatorText);
            }
        }
    }

    /**
     * 判断是否为已知的非字段关键词（分页/聚合/排序等）
     */
    private boolean isKnownKeyword(String word) {
        if (word == null) {
            return false;
        }

        // 分页关键词
        if (NLParserKeywords.isLimitKeyword(word) ||
                NLParserKeywords.isOffsetKeyword(word) ||
                NLParserKeywords.isPageKeyword(word) ||
                NLParserKeywords.isSizeKeyword(word) ||
                NLParserKeywords.isFromKeyword(word) ||
                NLParserKeywords.isContinueKeyword(word) ||
                NLParserKeywords.isRangeKeyword(word)) {
            return true;
        }

        // 排序关键词
        if (NLParserKeywords.isSortBoundary(word)) {
            return true;
        }

        // 索引关键词
        if (NLParserKeywords.isIndexIndicator(word)) {
            return true;
        }

        // 聚合关键词
        if (AggKeywords.isAggKeyword(word)) {
            return true;
        }

        // 常见的聚合辅助词（如"统计"）
        if ("统计".equals(word)) {
            return true;
        }

        return false;
    }

    /**
     * 在tokens中查找字段后可能的值
     */
    private String findPossibleValueAfterField(List<Token> tokens, String field) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token token = tokens.get(i);
            if (token.getText().equals(field)) {
                Token nextToken = tokens.get(i + 1);
                if (nextToken.getType() == TokenType.NUMBER ||
                        nextToken.getType() == TokenType.VALUE ||
                        nextToken.getType() == TokenType.UNKNOWN) {
                    Object value = nextToken.getValue() != null ? nextToken.getValue() : nextToken.getText();
                    return value.toString();
                }
            }
        }
        return null;
    }

    /**
     * 获取操作符的文本表示（用于错误消息）
     */
    private String getOperatorText(OperatorType operator) {
        if (operator == null) {
            return "操作符";
        }
        return operator.getDescription();
    }

    /**
     * 判断是否应该继续收集值
     */
    private boolean shouldContinueCollectingValues(List<Token> tokens, int currentIndex, OperatorType operator) {
        if (currentIndex + 1 >= tokens.size()) {
            return false;
        }

        Token nextToken = tokens.get(currentIndex + 1);

        // 1. 对于多值操作符，遇到分隔符时需要检查是否为子句边界
        if (operator.needsMultipleValues() && nextToken.getType() == TokenType.DELIMITER) {
            // 只有当分隔符不是子句边界时，才继续收集
            return !isClauseBoundary(tokens, currentIndex + 1);
        }
        // 2. 对于单值操作符，遇到OR则不继续（会在上面处理）
        else if (!operator.needsMultipleValues() &&
                nextToken.getType() == TokenType.LOGIC &&
                nextToken.getLogicType() == LogicType.OR) {
            return true; // 让下一次循环处理OR
        }
        // 3. 对于需要多个值的操作符（IN, BETWEEN等），如果下一个也是值类型，继续收集
        else if (operator.needsMultipleValues() &&
                (nextToken.getType() == TokenType.NUMBER ||
                        nextToken.getType() == TokenType.VALUE ||
                        nextToken.getType() == TokenType.UNKNOWN)) {
            return true;
        }

        return false;
    }

    /**
     * 构建条件并转换状态
     */
    private void buildConditionAndTransitionState(ConditionIntentBuilder builder,
                                                  String field,
                                                  OperatorType operator,
                                                  List<Object> values) {
        if (operator.needsMultipleValues()) {
            // IN, NOT_IN, BETWEEN等需要多个值的操作符，统一使用values列表
            builder.addCondition(field, operator, null, values);
        } else if (values.size() == 1) {
            // 单值操作符且只有1个值，使用value字段
            builder.addCondition(field, operator, values.get(0), null);
        } else {
            // 其他情况（理论上不应该发生）
            builder.addCondition(field, operator, null, values);
        }
    }

    /**
     * 判断当前位置（分隔符）是否为子句边界
     * 通过向前查看，检测分隔符后是否跟着以下模式之一：
     * 1. "字段+操作符"模式（新的条件子句）
     * 2. 排序关键词（"按"）
     * 3. 聚合关键词（"统计"、"平均"等）
     * 4. 分页关键词（"限制"、"跳过"、"返回"等）
     * <p>
     * 关键：只跳过连续的分隔符，然后检查第一个非分隔符token
     * 不跳过值token，避免误判
     *
     * @param tokens       所有tokens
     * @param currentIndex 当前分隔符的索引
     * @return true表示这是子句分隔符，false表示这是值分隔符
     */
    private boolean isClauseBoundary(List<Token> tokens, int currentIndex) {
        // 从分隔符后一个token开始查找
        int i = currentIndex + 1;

        // 第一步：只跳过连续的分隔符
        while (i < tokens.size() && tokens.get(i).getType() == TokenType.DELIMITER) {
            i++;
        }

        // 到达末尾，不是子句边界
        if (i >= tokens.size()) {
            return false;
        }

        Token token = tokens.get(i);
        String text = token.getText();

        // 第二步：检查第一个非分隔符token是否为子句边界标志

        // 1. 检测排序关键词（"按"）
        if (token.getType() == TokenType.UNKNOWN && NLParserKeywords.isSortBoundary(text)) {
            return true;
        }

        // 2. 检测聚合关键词
        if (token.getType() == TokenType.AGGREGATION) {
            return true;
        }

        // 3. 检测分页关键词
        if (token.getType() == TokenType.UNKNOWN &&
                (NLParserKeywords.isLimitKeyword(text) ||
                        NLParserKeywords.isOffsetKeyword(text) ||
                        NLParserKeywords.isPageKeyword(text) ||
                        NLParserKeywords.isSizeKeyword(text) ||
                        NLParserKeywords.isFromKeyword(text) ||
                        NLParserKeywords.isContinueKeyword(text))) {
            return true;
        }

        // 4. 检测"字段 + 操作符"模式
        // 如果当前token是UNKNOWN或FIELD_CANDIDATE，检查下一个是否为OPERATOR
        if (token.getType() == TokenType.UNKNOWN || token.getType() == TokenType.FIELD_CANDIDATE) {
            if (i + 1 < tokens.size() && tokens.get(i + 1).getType() == TokenType.OPERATOR) {
                return true;
            }
        }

        // 其他情况，认为是值分隔符
        return false;
    }

    /**
     * 条件意图构建器（辅助类）
     */
    private static class ConditionIntentBuilder {
        private final List<ConditionIntent> conditions = new ArrayList<>();
        private LogicType logic;

        public void addCondition(String fieldHint, OperatorType operator, Object value, List<Object> values) {
            ConditionIntent.ConditionIntentBuilder builder = ConditionIntent.builder()
                    .fieldHint(fieldHint)
                    .operator(operator)
                    .value(value);

            // 只有在values不为null时才设置，否则使用@Builder.Default
            if (values != null && !values.isEmpty()) {
                builder.values(values);
            }

            conditions.add(builder.build());
        }

        public void setLogic(LogicType logic) {
            this.logic = logic;
        }

        public boolean hasConditions() {
            return !conditions.isEmpty();
        }

        public ConditionIntent build() {
            if (conditions.isEmpty()) {
                return null;
            }

            if (conditions.size() == 1) {
                return conditions.get(0);
            }

            // 多个条件，需要组合
            ConditionIntent first = conditions.get(0);
            first.setLogic(logic != null ? logic : LogicType.AND);
            first.setChildren(conditions.subList(1, conditions.size()));

            return first;
        }
    }
}
