package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.NLParserKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 索引/表名提取器
 * <p>
 * 支持的模式：
 * - "查user索引" / "查user这个索引"
 * - "从user表" / "在user表中"
 * - "user索引" / "user表"
 * - "user_behavior索引"（支持下划线和复合名称）
 * <p>
 * 特性：
 * - 线程安全：无状态，所有方法都是纯函数
 * - 容错性：支持索引名被错误识别为LOGIC/OPERATOR类型（如behavior包含or）
 * - 自动清理：提取后自动从tokens列表中移除相关token
 *
 * @author surezzzzzz
 */
public class IndexExtractor {

    /**
     * 提取索引/表名提示并从tokens列表中移除相关token
     *
     * @param tokens token列表（会被修改，移除已识别的索引相关token）
     * @return 索引名，如果未找到则返回null
     */
    public String extractAndRemove(List<Token> tokens) {
        // 参数验证
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        // 遍历查找索引指示词
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // 只处理UNKNOWN类型的索引/表名标识关键词
            if (!isIndexIndicator(token)) {
                continue;
            }

            // 找到索引指示词，开始提取
            IndexExtractionResult result = extractIndexName(tokens, i);
            if (result != null && result.indexName != null) {
                removeTokensByIndices(tokens, result.tokensToRemove);
                return result.indexName;
            }
        }

        return null;
    }

    /**
     * 判断token是否为索引指示词
     */
    private boolean isIndexIndicator(Token token) {
        return token != null &&
                token.getType() == TokenType.UNKNOWN &&
                NLParserKeywords.isIndexIndicator(token.getText());
    }

    /**
     * 提取索引名（先向前查找，再向后查找）
     */
    private IndexExtractionResult extractIndexName(List<Token> tokens, int indicatorIndex) {
        // 优先向前查找（更常见的模式："user索引"）
        IndexExtractionResult backwardResult = searchBackwardForIndexName(tokens, indicatorIndex);
        if (backwardResult != null) {
            return backwardResult;
        }

        // 向后查找（次要模式："索引 user"）
        return searchForwardForIndexName(tokens, indicatorIndex);
    }

    /**
     * 向前查找索引名
     * 支持模式："user_behavior这个索引"、"user索引"
     */
    private IndexExtractionResult searchBackwardForIndexName(List<Token> tokens, int indicatorIndex) {
        if (indicatorIndex <= 0) {
            return null;
        }

        StringBuilder indexNameBuilder = new StringBuilder();
        List<Integer> indexNameIndices = new ArrayList<>();
        Integer demonstrativeIndex = null; // 指示词位置（"这个"、"那个"）
        int searchIndex = indicatorIndex - 1;

        // 向前扫描，收集索引名的所有token
        while (searchIndex >= 0) {
            Token token = tokens.get(searchIndex);
            String text = token.getText();

            // 检查是否为指示词
            if (NLParserKeywords.isDemonstrativeWord(text)) {
                demonstrativeIndex = searchIndex;
                searchIndex--;
                continue;
            }

            // 检查是否为索引名的一部分
            if (isValidIndexNameToken(token)) {
                indexNameIndices.add(0, searchIndex); // 插入到开头保持顺序
                searchIndex--;
                continue;
            }

            // 遇到其他类型token，停止查找
            break;
        }

        // 构建索引名
        if (indexNameIndices.isEmpty()) {
            return null;
        }

        // 按顺序拼接索引名
        for (Integer index : indexNameIndices) {
            indexNameBuilder.append(tokens.get(index).getText());
        }

        // 准备要移除的token索引列表
        List<Integer> tokensToRemove = new ArrayList<>(indexNameIndices);
        tokensToRemove.add(indicatorIndex); // 添加"索引"/"表"
        if (demonstrativeIndex != null) {
            tokensToRemove.add(demonstrativeIndex); // 添加"这个"等
        }

        return new IndexExtractionResult(indexNameBuilder.toString(), tokensToRemove);
    }

    /**
     * 向后查找索引名
     * 支持模式："索引user_behavior"、"表 user"
     */
    private IndexExtractionResult searchForwardForIndexName(List<Token> tokens, int indicatorIndex) {
        if (indicatorIndex >= tokens.size() - 1) {
            return null;
        }

        StringBuilder indexNameBuilder = new StringBuilder();
        List<Integer> indexNameIndices = new ArrayList<>();
        int searchIndex = indicatorIndex + 1;

        // 向后扫描，收集索引名的所有token
        while (searchIndex < tokens.size()) {
            Token token = tokens.get(searchIndex);

            // 检查是否为索引名的一部分
            if (isValidIndexNameToken(token)) {
                indexNameIndices.add(searchIndex);
                searchIndex++;
                continue;
            }

            // 遇到其他类型token，停止查找
            break;
        }

        // 构建索引名
        if (indexNameIndices.isEmpty()) {
            return null;
        }

        // 按顺序拼接索引名
        for (Integer index : indexNameIndices) {
            indexNameBuilder.append(tokens.get(index).getText());
        }

        // 准备要移除的token索引列表
        List<Integer> tokensToRemove = new ArrayList<>(indexNameIndices);
        tokensToRemove.add(indicatorIndex); // 添加"索引"/"表"

        return new IndexExtractionResult(indexNameBuilder.toString(), tokensToRemove);
    }

    /**
     * 判断token是否可以作为索引名的一部分
     * <p>
     * 支持类型：
     * - UNKNOWN：正常识别的索引名
     * - LOGIC：容错，因为像"user_behavior"中的"or"可能被识别为逻辑词
     * - OPERATOR：容错，因为下划线等符号可能被识别为操作符
     */
    private boolean isValidIndexNameToken(Token token) {
        if (token == null) {
            return false;
        }

        TokenType type = token.getType();
        return type == TokenType.UNKNOWN ||
                type == TokenType.LOGIC ||
                type == TokenType.OPERATOR;
    }

    /**
     * 从tokens列表中移除指定索引的tokens
     * 从后往前移除，避免索引变化导致的问题
     *
     * @param tokens          原始token列表
     * @param indicesToRemove 要移除的token索引列表
     */
    private void removeTokensByIndices(List<Token> tokens, List<Integer> indicesToRemove) {
        if (indicesToRemove == null || indicesToRemove.isEmpty()) {
            return;
        }

        // 按降序排序，从后往前删除
        indicesToRemove.sort((a, b) -> Integer.compare(b, a));

        // 移除tokens
        for (Integer index : indicesToRemove) {
            if (index >= 0 && index < tokens.size()) {
                tokens.remove((int) index);
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 索引提取结果
     */
    private static class IndexExtractionResult {
        final String indexName;
        final List<Integer> tokensToRemove;

        IndexExtractionResult(String indexName, List<Integer> tokensToRemove) {
            this.indexName = indexName;
            this.tokensToRemove = tokensToRemove;
        }
    }
}
