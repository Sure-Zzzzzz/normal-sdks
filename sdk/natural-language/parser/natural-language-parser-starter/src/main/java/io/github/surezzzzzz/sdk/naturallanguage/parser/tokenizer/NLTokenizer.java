package io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.AggKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.LogicKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.OperatorKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.SortKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.strategy.*;

import java.util.*;

/**
 * 自然语言分词器
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
public class NLTokenizer implements SplitContext {

    /**
     * 停用词集合（实例变量）
     */
    private final Set<String> stopWords;

    /**
     * Token拆分策略列表（按优先级排序）
     */
    private final List<TokenSplitStrategy> splitStrategies;

    public NLTokenizer() {
        this.stopWords = new HashSet<>();
        initDefaultStopWords();

        // 初始化拆分策略（按优先级排序）
        this.splitStrategies = Arrays.asList(
                new DelimiterSplitStrategy(),      // 先处理分隔符
                new OperatorSplitStrategy(),       // 再处理操作符
                new LogicKeywordSplitStrategy()    // 最后处理逻辑关键词
        );
    }

    /**
     * 初始化默认停用词
     */
    private void initDefaultStopWords() {
        // 常见停用词
        stopWords.add("的");
        stopWords.add("了");
        stopWords.add("在");
        stopWords.add("是");
        stopWords.add("我");
        stopWords.add("有");
        stopWords.add("和");
        stopWords.add("就");
        stopWords.add("不");
        stopWords.add("人");
        stopWords.add("都");
        stopWords.add("一");
        stopWords.add("一个");
        stopWords.add("上");
        stopWords.add("也");
        stopWords.add("很");
        // 注意："到"不能作为停用词，因为它是范围连接词（RANGE_KEYWORDS）
        stopWords.add("说");
        stopWords.add("要");
        stopWords.add("去");
        stopWords.add("你");
        stopWords.add("会");
        stopWords.add("着");
        stopWords.add("没有");
        stopWords.add("看");
        stopWords.add("好");
        stopWords.add("自己");
        stopWords.add("这");
        // 查询相关的停用词
        stopWords.add("查");
        stopWords.add("查询");
        stopWords.add("查一下");
        stopWords.add("找");
        stopWords.add("找一下");
        stopWords.add("搜索");
        stopWords.add("给");
        stopWords.add("给我");
        stopWords.add("帮");
        stopWords.add("帮我");
        stopWords.add("一下");
        // 注意："返回"不能作为停用词，因为它是分页关键词
        stopWords.add("数据");
        stopWords.add("条"); // 量词，分页时常用
        stopWords.add("就");
        stopWords.add("行");
    }

    /**
     * 分词并识别 Token
     */
    public List<Token> tokenize(String text) {
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
                    OperatorType combinedOp = OperatorKeywords.fromKeyword(combined);
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

        // 后处理：尝试合并相邻的UNKNOWN token以匹配关键词
        tokens = mergeKeywords(tokens);

        // 后处理：尝试合并相邻的OPERATOR token以匹配复合操作符（如"大于"+"等于"→"大于等于"）
        tokens = mergeOperators(tokens);

        return tokens;
    }

    /**
     * 合并相邻的UNKNOWN token以匹配关键词
     */
    private List<Token> mergeKeywords(List<Token> tokens) {
        List<Token> result = new ArrayList<>();
        int i = 0;

        while (i < tokens.size()) {
            Token current = tokens.get(i);

            // 只处理UNKNOWN类型的token
            if (current.getType() == TokenType.UNKNOWN && i + 1 < tokens.size()) {
                Token next = tokens.get(i + 1);

                if (next.getType() == TokenType.UNKNOWN) {
                    // 尝试合并2个token
                    String combined = current.getText() + next.getText();

                    // 检查合并后是否匹配任何关键词
                    Token merged = tryRecognizeKeyword(combined, current.getPosition());
                    if (merged != null && merged.getType() != TokenType.UNKNOWN) {
                        // 成功识别为关键词，使用合并后的token
                        result.add(merged);
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
                    OperatorType combinedOp = OperatorKeywords.fromKeyword(combined);
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
        OperatorType opType = OperatorKeywords.fromKeyword(text);
        if (opType != null) {
            return Token.operator(text, opType, position);
        }

        // 检查逻辑词
        LogicType logicType = LogicKeywords.fromKeyword(text);
        if (logicType != null) {
            return Token.logic(text, logicType, position);
        }

        // 检查聚合词
        AggType aggType = AggKeywords.fromKeyword(text);
        if (aggType != null) {
            return Token.aggregation(text, aggType, position);
        }

        // 检查排序词
        SortOrder sortOrder = SortKeywords.fromKeyword(text);
        if (sortOrder != null) {
            return Token.sort(text, sortOrder, position);
        }

        // 不是关键词，返回null
        return null;
    }

    /**
     * 识别 Token 类型
     *
     * @param word     词
     * @param position 位置
     * @return token
     */
    @Override
    public Token recognizeToken(String word, int position) {
        // 1. 检查是否为操作符
        OperatorType operatorType = OperatorKeywords.fromKeyword(word);
        if (operatorType != null) {
            return Token.operator(word, operatorType, position);
        }

        // 2. 检查是否为逻辑词
        LogicType logicType = LogicKeywords.fromKeyword(word);
        if (logicType != null) {
            return Token.logic(word, logicType, position);
        }

        // 3. 检查是否为聚合词
        AggType aggType = AggKeywords.fromKeyword(word);
        if (aggType != null) {
            return Token.aggregation(word, aggType, position);
        }

        // 4. 检查是否为排序词
        SortOrder sortOrder = SortKeywords.fromKeyword(word);
        if (sortOrder != null) {
            return Token.sort(word, sortOrder, position);
        }

        // 5. 检查是否为数值
        Object number = tryParseNumber(word);
        if (number != null) {
            return Token.number(word, number, position);
        }

        // 6. 检查是否为停用词
        if (isStopWord(word)) {
            return Token.builder()
                    .type(TokenType.STOP_WORD)
                    .text(word)
                    .position(position)
                    .build();
        }

        // 7. 检查是否为分隔符
        if (isDelimiter(word)) {
            return Token.builder()
                    .type(TokenType.DELIMITER)
                    .text(word)
                    .position(position)
                    .build();
        }

        // 8. 其他：可能是字段名或值
        return Token.unknown(word, position);
    }

    /**
     * 尝试解析数值
     */
    private Object tryParseNumber(String text) {
        try {
            // 尝试解析整数
            if (!text.contains(".")) {
                return Long.parseLong(text);
            }
            // 尝试解析浮点数
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否为停用词
     *
     * @param word 词
     * @return true表示是停用词
     */
    @Override
    public boolean isStopWord(String word) {
        return stopWords.contains(word);
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

    /**
     * 添加自定义停用词
     */
    public void addStopWord(String word) {
        stopWords.add(word);
    }

    /**
     * 批量添加停用词
     */
    public void addStopWords(Set<String> words) {
        if (words != null) {
            stopWords.addAll(words);
        }
    }
}
