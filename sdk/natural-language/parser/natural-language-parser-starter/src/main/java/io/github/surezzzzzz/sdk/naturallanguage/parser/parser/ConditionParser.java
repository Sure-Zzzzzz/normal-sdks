package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.ConditionIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 条件解析插件（核心）
 * 使用状态机解析自然语言条件表达式
 *
 * <p>设计模式：
 * <ul>
 *   <li>状态模式（State Pattern）：ParseState 驱动分派，handler 方法实现各状态行为</li>
 *   <li>上下文对象（Context Object）：ParseContext 封装全部可变状态，消除散落局部变量</li>
 *   <li>预处理管道（Pipeline）：normalizeTokens 在解析前拆分嵌入式逻辑关键词，简化 parser</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(20)
public class ConditionParser implements NLParserPlugin {

    // ──────────────────────────────────────────────
    // 状态定义
    // ──────────────────────────────────────────────

    private enum ParseState {
        EXPECT_FIELD,
        EXPECT_OPERATOR,
        EXPECT_VALUE,
        EXPECT_LOGIC_OR_END
    }

    // ──────────────────────────────────────────────
    // 解析上下文：封装全部可变状态
    // ──────────────────────────────────────────────

    static class ParseContext {
        final List<Token> tokens;
        final KeywordRegistry registry;
        final ConditionIntentBuilder builder = new ConditionIntentBuilder();

        ParseState state = ParseState.EXPECT_FIELD;
        String currentField;
        String lastField;
        OperatorType currentOperator;
        List<Object> currentValues = new ArrayList<>();
        boolean sameFieldOrMode;   // OR in EXPECT_VALUE → 下一 token 可能是同字段值
        int index;

        ParseContext(List<Token> tokens, KeywordRegistry registry) {
            this.tokens = tokens;
            this.registry = registry;
        }

        Token token() {
            return tokens.get(index);
        }

        TokenType type() {
            return token().getType();
        }

        boolean is(TokenType t) {
            return type() == t;
        }

        boolean isAny(TokenType... types) {
            for (TokenType t : types) if (type() == t) return true;
            return false;
        }

        void to(ParseState s) {
            this.state = s;
        }

        void resetAfterCondition() {
            lastField = currentField;
            currentField = null;
            currentOperator = null;
            currentValues = new ArrayList<>();
        }
    }

    // ──────────────────────────────────────────────
    // 入口
    // ──────────────────────────────────────────────

    @Override
    public boolean supports(IntentType intentType) {
        return true;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        ConditionIntent condition = doParse(tokens, keywordRegistry);
        if (condition != null) result.setCondition(condition);
    }

    private ConditionIntent doParse(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) return null;

        ParseContext ctx = new ParseContext(normalizeTokens(tokens, keywordRegistry), keywordRegistry);

        for (ctx.index = 0; ctx.index < ctx.tokens.size(); ctx.index++) {
            if (ctx.isAny(TokenType.AGGREGATION, TokenType.SORT)) continue;

            switch (ctx.state) {
                case EXPECT_FIELD:
                    handleExpectField(ctx);
                    break;
                case EXPECT_OPERATOR:
                    handleExpectOperator(ctx);
                    break;
                case EXPECT_VALUE:
                    handleExpectValue(ctx);
                    break;
                case EXPECT_LOGIC_OR_END:
                    handleExpectLogicOrEnd(ctx);
                    break;
            }
        }

        if (ctx.state == ParseState.EXPECT_VALUE && ctx.currentOperator != null) {
            throw NLParseException.missingValue(null, -1, ctx.currentOperator.getDescription());
        }
        return ctx.builder.build();
    }

    // ──────────────────────────────────────────────
    // 状态处理器
    // ──────────────────────────────────────────────

    private void handleExpectField(ParseContext ctx) {
        // 同字段 OR：当前 token 可能是同字段的值（如 "名字包含张或李" 中的 "李"）
        if (ctx.sameFieldOrMode && ctx.isAny(TokenType.UNKNOWN, TokenType.FIELD_CANDIDATE)) {
            ctx.sameFieldOrMode = false;
            if (!isNextTokenOperator(ctx)) {
                buildSameFieldOrValue(ctx);
                return;
            }
            ctx.currentOperator = null;  // fall through → 新字段
        }
        ctx.sameFieldOrMode = false;

        // 独立 NUMBER / VALUE
        if (ctx.isAny(TokenType.NUMBER, TokenType.VALUE)) {
            ctx.currentValues.add(tokenValue(ctx.token()));
            ctx.to(ParseState.EXPECT_LOGIC_OR_END);
            return;
        }

        // UNKNOWN / FIELD_CANDIDATE → 字段名（lookahead 合并连续 UNKNOWN）
        if (ctx.isAny(TokenType.UNKNOWN, TokenType.FIELD_CANDIDATE)) {
            ctx.currentField = collectFieldName(ctx);
            ctx.index = fieldNameLookAheadEnd(ctx);
            ctx.to(ParseState.EXPECT_OPERATOR);
        }
    }

    private void handleExpectOperator(ParseContext ctx) {
        if (ctx.is(TokenType.OPERATOR)) {
            ctx.currentOperator = ctx.token().getOperatorType();
            if (ctx.currentOperator.needsValue()) {
                ctx.to(ParseState.EXPECT_VALUE);
            } else {
                ctx.builder.addCondition(ctx.currentField, ctx.currentOperator, null, null);
                ctx.resetAfterCondition();
                ctx.to(ParseState.EXPECT_LOGIC_OR_END);
            }
            return;
        }

        if (ctx.is(TokenType.UNKNOWN) && isInOperatorContext(ctx)) {
            ctx.currentOperator = OperatorType.IN;
            ctx.to(ParseState.EXPECT_VALUE);
        }
    }

    private void handleExpectValue(ParseContext ctx) {
        // DELIMITER → 可能结束当前值收集
        if (ctx.is(TokenType.DELIMITER)) {
            handleDelimiterInValue(ctx);
            return;
        }

        // LOGIC(OR) → 同字段 OR 模式
        if (ctx.is(TokenType.LOGIC) && ctx.token().getLogicType() == LogicType.OR
                && !ctx.currentOperator.needsMultipleValues()) {
            if (!ctx.currentValues.isEmpty()) {
                ctx.builder.addCondition(ctx.currentField, ctx.currentOperator, ctx.currentValues.get(0), null);
                ctx.builder.setLogic(LogicType.OR);
            }
            ctx.currentValues = new ArrayList<>();
            ctx.sameFieldOrMode = true;
            ctx.to(ParseState.EXPECT_FIELD);
            return;
        }

        // NUMBER / VALUE / UNKNOWN → 收集值
        if (ctx.isAny(TokenType.NUMBER, TokenType.VALUE, TokenType.UNKNOWN)) {
            ctx.currentValues.add(tokenValue(ctx.token()));
            if (shouldContinueCollectingValues(ctx)) return;

            buildConditionAndTransitionState(ctx.builder, ctx.currentField, ctx.currentOperator, ctx.currentValues);
            ctx.resetAfterCondition();
            ctx.to(ParseState.EXPECT_LOGIC_OR_END);
        }
    }

    private void handleExpectLogicOrEnd(ParseContext ctx) {
        if (ctx.is(TokenType.LOGIC)) {
            ctx.builder.setLogic(ctx.token().getLogicType());
            ctx.lastField = null;
            ctx.to(ParseState.EXPECT_FIELD);
            return;
        }

        if (ctx.isAny(TokenType.UNKNOWN, TokenType.FIELD_CANDIDATE)) {
            ctx.currentField = ctx.token().getText();
            ctx.to(ParseState.EXPECT_OPERATOR);
            return;
        }

        if (ctx.is(TokenType.DELIMITER)) {
            if (ctx.builder.hasConditions()) ctx.builder.setLogic(LogicType.AND);
            ctx.lastField = null;
            ctx.to(ParseState.EXPECT_FIELD);
            return;
        }

        if (ctx.is(TokenType.OPERATOR)) {
            if (ctx.builder.hasConditions()) ctx.builder.setLogic(LogicType.AND);
            ctx.currentOperator = ctx.token().getOperatorType();
            ctx.currentField = ctx.lastField;
            if (ctx.currentOperator.needsValue()) {
                ctx.to(ParseState.EXPECT_VALUE);
            } else {
                ctx.builder.addCondition(ctx.currentField, ctx.currentOperator, null, null);
                ctx.resetAfterCondition();
                ctx.to(ParseState.EXPECT_LOGIC_OR_END);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Token 预处理：拆分嵌入式逻辑关键词 + 归一化 BETWEEN 模式
    // ──────────────────────────────────────────────

    /**
     * 预处理管道：
     * 1. 拆分嵌入式逻辑关键词
     * 2. 归一化 "在X到/至Y之间" → BETWEEN 操作符
     *
     * <p>例：["25或城市"] → ["25"(NUMBER), "或"(LOGIC), "城市"(UNKNOWN)]
     * <p>例：["年龄", "在", 18, "到", 30, "之间"] → ["年龄", BETWEEN, 18, 30]
     */
    private static List<Token> normalizeTokens(List<Token> tokens, KeywordRegistry registry) {
        if (registry == null) return tokens;
        List<Token> result = new ArrayList<>();
        for (Token token : tokens) {
            if (token.getType() != TokenType.UNKNOWN && token.getType() != TokenType.FIELD_CANDIDATE) {
                result.add(token);
                continue;
            }
            LogicType logic = findEmbeddedLogic(token.getText(), registry);
            if (logic == null) {
                result.add(token);
                continue;
            }
            // 拆分：[valuePart?, logicKeyword, remaining?]
            int idx = findLogicKeywordIndex(token.getText(), registry);
            int len = findLogicKeywordLen(token.getText(), idx, registry);
            int pos = token.getPosition();
            String valuePart = idx > 0 ? token.getText().substring(0, idx) : null;
            String logicWord = token.getText().substring(idx, idx + len);
            String remaining = (idx + len) < token.getText().length()
                    ? token.getText().substring(idx + len) : null;

            if (valuePart != null && !valuePart.isEmpty()) result.add(toValueToken(valuePart, pos));
            result.add(Token.logic(logicWord, logic, pos));
            if (remaining != null && !remaining.isEmpty()) result.add(Token.unknown(remaining, pos));
        }
        return normalizeBetweenPattern(result);
    }

    /**
     * 将 "在X到/至Y之间" 模式归一化为 BETWEEN 操作符 + 两个数值 token。
     * "到"/"至" 和 "之间" 被移除，与 "介于X,Y" 走相同的解析路径。
     */
    private static List<Token> normalizeBetweenPattern(List<Token> tokens) {
        boolean hasBetween = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (isBetweenStart(tokens, i)) {
                hasBetween = true;
                break;
            }
        }
        if (!hasBetween) return tokens;

        List<Token> result = new ArrayList<>();
        for (int i = 0; i < tokens.size(); ) {
            if (isBetweenStart(tokens, i)) {
                result.add(Token.operator("在...之间", OperatorType.BETWEEN, tokens.get(i).getPosition()));
                result.add(tokens.get(i + 1));
                result.add(tokens.get(i + 3));
                i += 4;
                if (i < tokens.size() && tokens.get(i).getType() == TokenType.UNKNOWN
                        && "之间".equals(tokens.get(i).getText())) {
                    i++;
                }
            } else {
                result.add(tokens.get(i));
                i++;
            }
        }
        return result;
    }

    /**
     * 检查位置 i 是否为 BETWEEN 模式起点：UNKNOWN("在") + NUMBER + UNKNOWN("到"/"至") + NUMBER
     */
    private static boolean isBetweenStart(List<Token> tokens, int i) {
        if (i + 3 >= tokens.size()) return false;
        if (tokens.get(i).getType() != TokenType.UNKNOWN
                || !"在".equals(tokens.get(i).getText())) return false;
        if (tokens.get(i + 1).getType() != TokenType.NUMBER) return false;
        Token rangeToken = tokens.get(i + 2);
        if (rangeToken.getType() != TokenType.UNKNOWN) return false;
        if (!"到".equals(rangeToken.getText()) && !"至".equals(rangeToken.getText())) return false;
        return tokens.get(i + 3).getType() == TokenType.NUMBER;
    }

    /**
     * 根据文本内容创建 NUMBER 或 UNKNOWN token
     */
    private static Token toValueToken(String text, int pos) {
        try {
            if (!text.contains(".")) return Token.number(text, Long.parseLong(text), pos);
            else return Token.number(text, Double.parseDouble(text), pos);
        } catch (NumberFormatException e) {
            return Token.unknown(text, pos);
        }
    }

    // ──────────────────────────────────────────────
    // 值处理辅助
    // ──────────────────────────────────────────────

    /**
     * 构建同字段 OR 值 condition 并重置状态（保留 currentField + currentOperator 供链式 OR 使用）
     */
    private void buildSameFieldOrValue(ParseContext ctx) {
        Object value = tokenValue(ctx.token());
        ctx.builder.addCondition(ctx.currentField, ctx.currentOperator, value, null);
        ctx.lastField = ctx.currentField;
        ctx.currentValues = new ArrayList<>();
        ctx.to(ParseState.EXPECT_LOGIC_OR_END);
    }

    /**
     * 从 token 提取值：优先 parsed value，fallback 文本
     */
    private static Object tokenValue(Token token) {
        return token.getValue() != null ? token.getValue() : token.getText();
    }

    /**
     * DELIMITER 在 EXPECT_VALUE 中的处理
     */
    private void handleDelimiterInValue(ParseContext ctx) {
        boolean isNewClause = isClauseBoundary(ctx.tokens, ctx.index, ctx.registry);

        // IN 等多值操作符：非子句边界时继续收集
        if (ctx.currentOperator.needsMultipleValues() && !ctx.currentValues.isEmpty() && !isNewClause) return;
        if (ctx.currentValues.isEmpty()) return;

        buildConditionAndTransitionState(ctx.builder, ctx.currentField, ctx.currentOperator, ctx.currentValues);
        ctx.resetAfterCondition();
        ctx.to(ParseState.EXPECT_LOGIC_OR_END);
        ctx.index--;  // 重新处理此 delimiter（可能是子句边界）
    }

    // ──────────────────────────────────────────────
    // 字段名收集
    // ──────────────────────────────────────────────

    private String collectFieldName(ParseContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.token().getText());
        int look = ctx.index + 1;
        while (look < ctx.tokens.size()) {
            Token next = ctx.tokens.get(look);
            if (next.getType() != TokenType.UNKNOWN && next.getType() != TokenType.FIELD_CANDIDATE) break;
            if (isInOperatorContext(ctx.tokens, look, ctx.registry)) break;
            sb.append(next.getText());
            look++;
        }
        return sb.toString();
    }

    private int fieldNameLookAheadEnd(ParseContext ctx) {
        int look = ctx.index + 1;
        while (look < ctx.tokens.size()) {
            Token next = ctx.tokens.get(look);
            if (next.getType() != TokenType.UNKNOWN && next.getType() != TokenType.FIELD_CANDIDATE) break;
            if (isInOperatorContext(ctx.tokens, look, ctx.registry)) break;
            look++;
        }
        return look - 1;
    }

    // ──────────────────────────────────────────────
    // 辅助判断
    // ──────────────────────────────────────────────

    private static LogicType findEmbeddedLogic(String text, KeywordRegistry registry) {
        if (registry == null || text == null) return null;
        for (LogicType lt : LogicType.values()) {
            for (String kw : registry.getLogicKeywords(lt)) {
                if (text.contains(kw)) return lt;
            }
        }
        return null;
    }

    private static int findLogicKeywordIndex(String text, KeywordRegistry registry) {
        if (text == null || registry == null) return -1;
        int bestIdx = -1;
        for (LogicType lt : LogicType.values()) {
            for (String kw : registry.getLogicKeywords(lt)) {
                int idx = text.indexOf(kw);
                if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) bestIdx = idx;
            }
        }
        return bestIdx;
    }

    private static int findLogicKeywordLen(String text, int logicIdx, KeywordRegistry registry) {
        if (text == null || logicIdx < 0 || registry == null) return 0;
        for (LogicType lt : LogicType.values()) {
            for (String kw : registry.getLogicKeywords(lt)) {
                if (text.regionMatches(logicIdx, kw, 0, kw.length())) return kw.length();
            }
        }
        return 1;
    }

    private boolean isNextTokenOperator(ParseContext ctx) {
        for (int j = ctx.index + 1; j < ctx.tokens.size(); j++) {
            TokenType t = ctx.tokens.get(j).getType();
            if (t == TokenType.OPERATOR) return true;
            if (t != TokenType.UNKNOWN && t != TokenType.FIELD_CANDIDATE) return false;
        }
        return false;
    }

    private boolean isInOperatorContext(ParseContext ctx) {
        return isInOperatorContext(ctx.tokens, ctx.index, ctx.registry);
    }

    private boolean isInOperatorContext(List<Token> tokens, int currentIndex, KeywordRegistry registry) {
        if (!"在".equals(tokens.get(currentIndex).getText())) return false;
        boolean foundValue = false;
        for (int j = currentIndex + 1; j < Math.min(tokens.size(), currentIndex + 6); j++) {
            TokenType t = tokens.get(j).getType();
            if (t == TokenType.NUMBER || t == TokenType.VALUE || t == TokenType.UNKNOWN) foundValue = true;
            else if (t == TokenType.DELIMITER && foundValue) return true;
            else if (t == TokenType.OPERATOR || t == TokenType.LOGIC
                    || t == TokenType.AGGREGATION || t == TokenType.SORT) break;
        }
        return false;
    }

    private boolean isClauseBoundary(List<Token> tokens, int currentIndex, KeywordRegistry registry) {
        int i = currentIndex + 1;
        while (i < tokens.size() && tokens.get(i).getType() == TokenType.DELIMITER) i++;
        if (i >= tokens.size()) return false;

        Token token = tokens.get(i);
        String text = token.getText();

        if (registry != null && token.getType() == TokenType.UNKNOWN
                && (registry.isPreposition(text) || registry.isPaginationKeyword(text)
                || registry.isRangeKeyword(text))) return true;
        if (token.getType() == TokenType.AGGREGATION || token.getType() == TokenType.PAGINATION) return true;
        if ((token.getType() == TokenType.UNKNOWN || token.getType() == TokenType.FIELD_CANDIDATE)
                && i + 1 < tokens.size() && tokens.get(i + 1).getType() == TokenType.OPERATOR) return true;
        return false;
    }

    private boolean shouldContinueCollectingValues(ParseContext ctx) {
        if (ctx.index + 1 >= ctx.tokens.size()) return false;
        Token next = ctx.tokens.get(ctx.index + 1);
        OperatorType op = ctx.currentOperator;

        if (op.needsMultipleValues() && next.getType() == TokenType.DELIMITER)
            return !isClauseBoundary(ctx.tokens, ctx.index + 1, ctx.registry);
        if (!op.needsMultipleValues() && next.getType() == TokenType.LOGIC && next.getLogicType() == LogicType.OR)
            return true;
        if (op.needsMultipleValues() && (next.getType() == TokenType.NUMBER
                || next.getType() == TokenType.VALUE || next.getType() == TokenType.UNKNOWN))
            return true;
        return false;
    }

    // ──────────────────────────────────────────────
    // Condition 构建
    // ──────────────────────────────────────────────

    private void buildConditionAndTransitionState(ConditionIntentBuilder builder,
                                                  String field, OperatorType operator, List<Object> values) {
        trimTrailingZhong(operator, values);

        if (operator.needsMultipleValues()) builder.addCondition(field, operator, null, values);
        else if (values.size() == 1) builder.addCondition(field, operator, values.get(0), null);
        else builder.addCondition(field, operator, null, values);
    }

    /**
     * IN 操作符：截断末尾"中"（如"深圳中"→"深圳"）
     */
    private static void trimTrailingZhong(OperatorType operator, List<Object> values) {
        if (operator != OperatorType.IN || values.isEmpty()) return;
        Object last = values.get(values.size() - 1);
        if (!(last instanceof String)) return;
        String s = (String) last;
        if (!s.endsWith("中")) return;
        String trimmed = s.substring(0, s.length() - 1);
        if (trimmed.isEmpty()) values.remove(values.size() - 1);
        else values.set(values.size() - 1, trimmed);
    }

    // ──────────────────────────────────────────────
    // Builder
    // ──────────────────────────────────────────────

    private static class ConditionIntentBuilder {
        private List<ConditionIntent> conditions = new ArrayList<>();
        private LogicType logic;
        private int orGroupIndex = -1;

        public void addCondition(String fieldHint, OperatorType operator, Object value, List<Object> values) {
            ConditionIntent.ConditionIntentBuilder b = ConditionIntent.builder()
                    .fieldHint(fieldHint).operator(operator).value(value);
            if (values != null && !values.isEmpty()) b.values(values);
            ConditionIntent newCond = b.build();

            if (orGroupIndex >= 0 && orGroupIndex < conditions.size()) {
                conditions.get(orGroupIndex).getChildren().add(newCond);
            } else {
                conditions.add(newCond);
            }
        }

        public void setLogic(LogicType logic) {
            this.logic = logic;
            if (logic == LogicType.OR && !conditions.isEmpty()) {
                if (orGroupIndex >= 0) return;  // 已在 OR 模式，不重复打包
                int lastIdx = conditions.size() - 1;
                ConditionIntent lastCond = conditions.get(lastIdx);
                conditions.set(lastIdx, ConditionIntent.builder()
                        .logic(LogicType.OR)
                        .fieldHint(lastCond.getFieldHint())
                        .operator(lastCond.getOperator())
                        .children(new ArrayList<>(Collections.singletonList(lastCond)))
                        .build());
                orGroupIndex = lastIdx;
            } else if (logic == LogicType.AND) {
                orGroupIndex = -1;
            }
        }

        public boolean hasConditions() {
            return !conditions.isEmpty();
        }

        public ConditionIntent build() {
            if (conditions.isEmpty()) return null;
            if (conditions.size() == 1) return conditions.get(0);
            ConditionIntent first = conditions.get(0);
            first.setLogic(logic != null ? logic : LogicType.AND);
            first.setChildren(new ArrayList<>(conditions.subList(1, conditions.size())));
            return first;
        }
    }
}
