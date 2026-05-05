package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy.*;

import java.util.*;

/**
 * 基于 HanLP 的默认分词器实现
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
public class DefaultNLTokenizer implements NLTokenizer, SplitContext {

    private final KeywordRegistry keywordRegistry;

    /**
     * Token拆分策略列表（按优先级排序）
     */
    private final List<TokenSplitStrategy> splitStrategies;

    public DefaultNLTokenizer(KeywordRegistry keywordRegistry) {
        this.keywordRegistry = keywordRegistry;
        this.splitStrategies = Arrays.asList(
                new DelimiterSplitStrategy(),
                new OperatorSplitStrategy(keywordRegistry),
                new LogicKeywordSplitStrategy(keywordRegistry)
        );
    }

    @Override
    public List<Token> tokenize(String text, KeywordRegistry keywordRegistry) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 使用 HanLP 分词
        List<Term> terms = HanLP.segment(text);
        List<Token> tokens = new ArrayList<>();

        mainLoop:
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            String word = term.word;

            // 跳过空白
            if (word.trim().isEmpty()) {
                continue;
            }

            // 处理复合操作符（如 >= 可能被分成 > 和 =）
            if (i + 1 < terms.size() && isOperatorSymbol(word)) {
                String nextWord = terms.get(i + 1).word;
                if (isOperatorSymbol(nextWord)) {
                    String combined = word + nextWord;
                    OperatorType combinedOp = keywordRegistry.resolveOperator(combined);
                    if (combinedOp != null) {
                        tokens.add(Token.operator(combined, combinedOp, i));
                        i++; // 跳过下一个 token
                        continue;
                    }
                }
            }

            // 使用策略模式处理复合token拆分
            for (TokenSplitStrategy strategy : splitStrategies) {
                if (strategy.canHandle(word)) {
                    List<Token> splitTokens = strategy.split(word, i, this);
                    tokens.addAll(splitTokens);
                    continue mainLoop;
                }
            }

            // 未匹配任何拆分策略，直接识别
            Token token = recognizeToken(word, i);

            // 跳过停用词
            if (token.getType() == TokenType.STOP_WORD) {
                continue;
            }

            tokens.add(token);
        }

        // 后处理：合并关键词（两轮：第一轮合并UNKNOWN+UNKNOWN，第二轮处理跨类型合并）
        tokens = mergeKeywords(tokens);
        tokens = mergeKeywords(tokens);

        // 后处理：尝试合并相邻的OPERATOR token以匹配复合操作符
        tokens = mergeOperators(tokens);

        return tokens;
    }

    /**
     * 合并相邻的UNKNOWN token以匹配关键词，同时处理跨类型合并：
     * - UNKNOWN + UNKNOWN → keyword（原有逻辑）
     * - LOGIC(NOT) + OPERATOR → OPERATOR（"不"+"等于"→"不等于"）
     * - UNKNOWN + OPERATOR → OPERATOR（"开头"+"是"→"开头是"）
     * - COLLAPSE + AGGREGATION → AGGREGATION（"去重"+"计数"→"去重计数"）
     */
    private List<Token> mergeKeywords(List<Token> tokens) {
        List<Token> result = new ArrayList<>();
        int i = 0;

        while (i < tokens.size()) {
            Token current = tokens.get(i);

            if (i + 1 < tokens.size()) {
                Token next = tokens.get(i + 1);
                String combined = current.getText() + next.getText();

                // UNKNOWN + UNKNOWN → keyword
                if (current.getType() == TokenType.UNKNOWN && next.getType() == TokenType.UNKNOWN) {
                    Token merged = tryRecognizeKeyword(combined, current.getPosition());
                    if (merged != null && merged.getType() != TokenType.UNKNOWN) {
                        result.add(merged);
                        i += 2;
                        continue;
                    }
                }

                // LOGIC(NOT) + OPERATOR → OPERATOR（"不"+"等于"→"不等于"，"不"+"包含"→"不包含"）
                if (current.getType() == TokenType.LOGIC && current.getLogicType() == LogicType.NOT
                        && next.getType() == TokenType.OPERATOR) {
                    OperatorType combinedOp = keywordRegistry.resolveOperator(combined);
                    if (combinedOp != null) {
                        result.add(Token.operator(combined, combinedOp, current.getPosition()));
                        i += 2;
                        continue;
                    }
                }

                // UNKNOWN + OPERATOR → OPERATOR（"开头"+"是"→"开头是"，"结尾"+"是"→"结尾是"）
                if (current.getType() == TokenType.UNKNOWN && next.getType() == TokenType.OPERATOR) {
                    OperatorType combinedOp = keywordRegistry.resolveOperator(combined);
                    if (combinedOp != null) {
                        result.add(Token.operator(combined, combinedOp, current.getPosition()));
                        i += 2;
                        continue;
                    }
                }

                // COLLAPSE + AGGREGATION → AGGREGATION（"去重"+"计数"→"去重计数"→CARDINALITY）
                if (current.getType() == TokenType.COLLAPSE && next.getType() == TokenType.AGGREGATION) {
                    AggType combinedAgg = keywordRegistry.resolveAggType(combined);
                    if (combinedAgg != null) {
                        result.add(Token.aggregation(combined, combinedAgg, current.getPosition()));
                        i += 2;
                        continue;
                    }
                }
            }

            result.add(current);
            i++;
        }

        return result;
    }

    /**
     * 合并相邻的OPERATOR token以匹配复合操作符
     * 例如："大于" + "等于" → "大于等于" (GTE)
     */
    private List<Token> mergeOperators(List<Token> tokens) {
        List<Token> result = new ArrayList<>();
        int i = 0;

        while (i < tokens.size()) {
            Token current = tokens.get(i);

            // 只处理OPERATOR类型的token
            if (current.getType() == TokenType.OPERATOR && i + 1 < tokens.size()) {
                Token next = tokens.get(i + 1);

                if (next.getType() == TokenType.OPERATOR) {
                    // 尝试合并2个操作符
                    String combined = current.getText() + next.getText();

                    // 检查合并后是否匹配复合操作符
                    OperatorType combinedOp = keywordRegistry.resolveOperator(combined);
                    if (combinedOp != null && combinedOp != current.getOperatorType()) {
                        // 成功识别为复合操作符，使用合并后的token
                        result.add(Token.operator(combined, combinedOp, current.getPosition()));
                        i += 2; // 跳过两个token
                        continue;
                    }
                }
            }

            // 没有合并，保留原token
            result.add(current);
            i++;
        }

        return result;
    }

    /**
     * 尝试将文本识别为关键词（仅检查关键词，不检查数值等）
     */
    private Token tryRecognizeKeyword(String text, int position) {
        // 检查操作符
        OperatorType opType = keywordRegistry.resolveOperator(text);
        if (opType != null) {
            return Token.operator(text, opType, position);
        }

        // 检查逻辑词
        LogicType logicType = keywordRegistry.resolveLogic(text);
        if (logicType != null) {
            return Token.logic(text, logicType, position);
        }

        // 检查聚合词
        AggType aggType = keywordRegistry.resolveAggType(text);
        if (aggType != null) {
            return Token.aggregation(text, aggType, position);
        }

        // 检查排序词
        SortOrder sortOrder = keywordRegistry.resolveSortOrder(text);
        if (sortOrder != null) {
            return Token.sort(text, sortOrder, position);
        }

        // 检查折叠词
        if (keywordRegistry.isCollapseKeyword(text)) {
            return Token.collapse(text, position);
        }

        // 检查时间范围词
        TimeRange timeRange = keywordRegistry.resolveTimeRange(text);
        if (timeRange != null) {
            return Token.timeRange(text, timeRange, position);
        }

        // 检查分页词
        if (keywordRegistry.isPaginationKeyword(text)) {
            return Token.pagination(text, position);
        }

        // 检查介词
        if (keywordRegistry.isPreposition(text)) {
            return Token.preposition(text, position);
        }

        // 不是关键词，返回null
        return null;
    }

    @Override
    public Token recognizeToken(String word, int position) {
        // 1. 检查是否为操作符
        OperatorType operatorType = keywordRegistry.resolveOperator(word);
        if (operatorType != null) {
            return Token.operator(word, operatorType, position);
        }

        // 2. 检查是否为逻辑词
        LogicType logicType = keywordRegistry.resolveLogic(word);
        if (logicType != null) {
            return Token.logic(word, logicType, position);
        }

        // 3. 检查是否为聚合词
        AggType aggType = keywordRegistry.resolveAggType(word);
        if (aggType != null) {
            return Token.aggregation(word, aggType, position);
        }

        // 4. 检查是否为排序词
        SortOrder sortOrder = keywordRegistry.resolveSortOrder(word);
        if (sortOrder != null) {
            return Token.sort(word, sortOrder, position);
        }

        // 5. 检查是否为折叠词
        if (keywordRegistry.isCollapseKeyword(word)) {
            return Token.collapse(word, position);
        }

        // 6. 检查是否为时间范围词
        TimeRange timeRange = keywordRegistry.resolveTimeRange(word);
        if (timeRange != null) {
            return Token.timeRange(word, timeRange, position);
        }

        // 7. 检查是否为分页词
        if (keywordRegistry.isPaginationKeyword(word)) {
            return Token.pagination(word, position);
        }

        // 8. 检查是否为介词
        if (keywordRegistry.isPreposition(word)) {
            return Token.preposition(word, position);
        }

        // 9. 检查是否为数值
        Object number = tryParseNumber(word);
        if (number != null) {
            return Token.number(word, number, position);
        }

        // 10. 检查是否为停用词
        if (keywordRegistry.isStopWord(word)) {
            return Token.builder()
                    .type(TokenType.STOP_WORD)
                    .text(word)
                    .position(position)
                    .build();
        }

        // 11. 检查是否为分隔符
        if (isDelimiter(word)) {
            return Token.builder()
                    .type(TokenType.DELIMITER)
                    .text(word)
                    .position(position)
                    .build();
        }

        // 12. 其他：可能是字段名或值
        return Token.unknown(word, position);
    }

    @Override
    public boolean isStopWord(String word) {
        return keywordRegistry.isStopWord(word);
    }

    /**
     * 尝试解析数值
     */
    private Object tryParseNumber(String text) {
        try {
            if (!text.contains(".")) {
                return Long.parseLong(text);
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否为分隔符
     */
    private boolean isDelimiter(String word) {
        return word.matches("[,，、;；]");
    }

    /**
     * 判断是否为操作符符号
     */
    private boolean isOperatorSymbol(String word) {
        return word.length() == 1 && ">=<!=".contains(word);
    }
}
