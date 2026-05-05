package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.ConditionIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件解析插件（核心）
 * 使用状态机解析自然语言条件表达式
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(20)
public class ConditionParser implements NLParserPlugin {

    private enum ParseState {
        EXPECT_FIELD,
        EXPECT_OPERATOR,
        EXPECT_VALUE,
        EXPECT_LOGIC_OR_END
    }

    @Override
    public boolean supports(IntentType intentType) {
        return true;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        ConditionIntent condition = doParse(tokens, keywordRegistry);
        if (condition != null) {
            result.setCondition(condition);
        }
    }

    private ConditionIntent doParse(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        ConditionIntentBuilder rootBuilder = new ConditionIntentBuilder();
        ConditionIntentBuilder currentBuilder = rootBuilder;

        ParseState state = ParseState.EXPECT_FIELD;
        String currentField = null;
        String lastField = null;
        OperatorType currentOperator = null;
        List<Object> currentValues = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() == TokenType.AGGREGATION || token.getType() == TokenType.SORT) {
                continue;
            }

            switch (state) {
                case EXPECT_FIELD:
                    if (token.getType() == TokenType.UNKNOWN || token.getType() == TokenType.FIELD_CANDIDATE) {
                        StringBuilder fieldBuilder = new StringBuilder(token.getText());
                        int lookAhead = i + 1;
                        while (lookAhead < tokens.size()) {
                            Token nextToken = tokens.get(lookAhead);
                            if (nextToken.getType() == TokenType.UNKNOWN ||
                                    nextToken.getType() == TokenType.FIELD_CANDIDATE) {
                                // 如果下一个 UNKNOWN token 是 IN 操作符上下文（如 "在北京、上海" 中的 "在"），停止收集
                                if (isInOperatorContext(tokens, lookAhead, keywordRegistry)) {
                                    break;
                                }
                                fieldBuilder.append(nextToken.getText());
                                lookAhead++;
                            } else {
                                break;
                            }
                        }
                        currentField = fieldBuilder.toString();
                        i = lookAhead - 1;
                        state = ParseState.EXPECT_OPERATOR;
                    }
                    break;

                case EXPECT_OPERATOR:
                    if (token.getType() == TokenType.OPERATOR) {
                        currentOperator = token.getOperatorType();
                        if (currentOperator.needsValue()) {
                            state = ParseState.EXPECT_VALUE;
                        } else {
                            currentBuilder.addCondition(currentField, currentOperator, null, null);
                            lastField = currentField;
                            currentField = null;
                            currentOperator = null;
                            state = ParseState.EXPECT_LOGIC_OR_END;
                        }
                    } else if (token.getType() == TokenType.UNKNOWN &&
                            isInOperatorContext(tokens, i, keywordRegistry)) {
                        // "城市在北京、上海" 中的 "在" — 后面跟分隔符+值模式 → IN
                        currentOperator = OperatorType.IN;
                        state = ParseState.EXPECT_VALUE;
                    }
                    break;

                case EXPECT_VALUE:
                    if (token.getType() == TokenType.DELIMITER) {
                        boolean isNewClause = isClauseBoundary(tokens, i, keywordRegistry);

                        if (currentOperator != null && currentOperator.needsMultipleValues() &&
                                !currentValues.isEmpty() && !isNewClause) {
                            continue;
                        }
                        if (currentValues.isEmpty()) {
                            continue;
                        }
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
                        i--;
                        break;
                    }

                    if (token.getType() == TokenType.LOGIC &&
                            token.getLogicType() == LogicType.OR &&
                            !currentOperator.needsMultipleValues()) {
                        if (!currentValues.isEmpty()) {
                            currentBuilder.addCondition(currentField, currentOperator, currentValues.get(0), null);
                            currentBuilder.setLogic(LogicType.OR);
                        }
                        currentValues = new ArrayList<>();
                        continue;
                    }

                    if (token.getType() == TokenType.NUMBER ||
                            token.getType() == TokenType.VALUE ||
                            token.getType() == TokenType.UNKNOWN) {

                        Object value = token.getValue() != null ? token.getValue() : token.getText();
                        currentValues.add(value);

                        boolean hasMore = shouldContinueCollectingValues(tokens, i, currentOperator, keywordRegistry);

                        if (hasMore) {
                            continue;
                        }

                        buildConditionAndTransitionState(currentBuilder, currentField, currentOperator, currentValues);

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
                        lastField = null;
                        state = ParseState.EXPECT_FIELD;
                    } else if (token.getType() == TokenType.DELIMITER) {
                        if (currentBuilder.hasConditions()) {
                            currentBuilder.setLogic(LogicType.AND);
                        }
                        lastField = null;
                        state = ParseState.EXPECT_FIELD;
                    } else if (token.getType() == TokenType.OPERATOR) {
                        if (currentBuilder.hasConditions()) {
                            currentBuilder.setLogic(LogicType.AND);
                        }
                        currentOperator = token.getOperatorType();
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
                        if (currentBuilder.hasConditions()) {
                            currentBuilder.setLogic(LogicType.AND);
                        }
                        lastField = null;
                        currentField = token.getText();
                        // 检查是否 "在" 处于 IN 操作符上下文（复用前一个字段的 IN 操作）
                        if ("在".equals(token.getText()) && isInOperatorContext(tokens, i, keywordRegistry)) {
                            currentField = lastField != null ? lastField : currentField;
                            currentOperator = OperatorType.IN;
                            state = ParseState.EXPECT_VALUE;
                        } else {
                            state = ParseState.EXPECT_OPERATOR;
                        }
                    }
                    break;
            }
        }

        if (state == ParseState.EXPECT_VALUE && currentOperator != null) {
            throw NLParseException.missingValue(null, -1, currentOperator.getDescription());
        }

        return rootBuilder.build();
    }

    /**
     * 判断 "在" 是否处于 IN 操作符上下文
     * 模式：字段名 + "在" + 值 + 分隔符 + 值 ...
     * 例如："城市在北京、上海、深圳" 中的 "在" 就是 IN 操作符
     */
    private boolean isInOperatorContext(List<Token> tokens, int currentIndex, KeywordRegistry keywordRegistry) {
        String text = tokens.get(currentIndex).getText();
        // 只对 "在" 和 "在...中" 相关的关键词判断
        if (!"在".equals(text)) {
            return false;
        }

        // 检查后面是否有值+分隔符+值的模式（IN 的典型模式）
        // 跳过可能的停用词等，看后面是否有 DELIMITER
        boolean foundValue = false;
        for (int j = currentIndex + 1; j < Math.min(tokens.size(), currentIndex + 6); j++) {
            Token next = tokens.get(j);
            if (next.getType() == TokenType.NUMBER || next.getType() == TokenType.VALUE ||
                    next.getType() == TokenType.UNKNOWN) {
                foundValue = true;
            } else if (next.getType() == TokenType.DELIMITER && foundValue) {
                // 找到了 "值 + 分隔符" 模式，这是 IN 操作符的强信号
                return true;
            } else if (next.getType() == TokenType.OPERATOR || next.getType() == TokenType.LOGIC ||
                    next.getType() == TokenType.AGGREGATION || next.getType() == TokenType.SORT) {
                break;
            }
        }
        return false;
    }

    private boolean isClauseBoundary(List<Token> tokens, int currentIndex, KeywordRegistry keywordRegistry) {
        int i = currentIndex + 1;

        while (i < tokens.size() && tokens.get(i).getType() == TokenType.DELIMITER) {
            i++;
        }

        if (i >= tokens.size()) {
            return false;
        }

        Token token = tokens.get(i);
        String text = token.getText();

        if (keywordRegistry != null && token.getType() == TokenType.UNKNOWN && keywordRegistry.isPreposition(text)) {
            return true;
        }
        if (token.getType() == TokenType.AGGREGATION) {
            return true;
        }
        if (keywordRegistry != null && token.getType() == TokenType.UNKNOWN &&
                (keywordRegistry.isPaginationKeyword(text) || keywordRegistry.isRangeKeyword(text))) {
            return true;
        }
        if (token.getType() == TokenType.PAGINATION) {
            return true;
        }
        if (token.getType() == TokenType.UNKNOWN || token.getType() == TokenType.FIELD_CANDIDATE) {
            if (i + 1 < tokens.size() && tokens.get(i + 1).getType() == TokenType.OPERATOR) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldContinueCollectingValues(List<Token> tokens, int currentIndex,
                                                   OperatorType operator, KeywordRegistry keywordRegistry) {
        if (currentIndex + 1 >= tokens.size()) {
            return false;
        }

        Token nextToken = tokens.get(currentIndex + 1);

        if (operator.needsMultipleValues() && nextToken.getType() == TokenType.DELIMITER) {
            return !isClauseBoundary(tokens, currentIndex + 1, keywordRegistry);
        } else if (!operator.needsMultipleValues() &&
                nextToken.getType() == TokenType.LOGIC &&
                nextToken.getLogicType() == LogicType.OR) {
            return true;
        } else if (operator.needsMultipleValues() &&
                (nextToken.getType() == TokenType.NUMBER ||
                        nextToken.getType() == TokenType.VALUE ||
                        nextToken.getType() == TokenType.UNKNOWN)) {
            return true;
        }

        return false;
    }

    private void buildConditionAndTransitionState(ConditionIntentBuilder builder,
                                                  String field,
                                                  OperatorType operator,
                                                  List<Object> values) {
        if (operator.needsMultipleValues()) {
            builder.addCondition(field, operator, null, values);
        } else if (values.size() == 1) {
            builder.addCondition(field, operator, values.get(0), null);
        } else {
            builder.addCondition(field, operator, null, values);
        }
    }

    private static class ConditionIntentBuilder {
        private final List<ConditionIntent> conditions = new ArrayList<>();
        private LogicType logic;

        public void addCondition(String fieldHint, OperatorType operator, Object value, List<Object> values) {
            ConditionIntent.ConditionIntentBuilder builder = ConditionIntent.builder()
                    .fieldHint(fieldHint)
                    .operator(operator)
                    .value(value);

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

            ConditionIntent first = conditions.get(0);
            first.setLogic(logic != null ? logic : LogicType.AND);
            first.setChildren(conditions.subList(1, conditions.size()));

            return first;
        }
    }
}
