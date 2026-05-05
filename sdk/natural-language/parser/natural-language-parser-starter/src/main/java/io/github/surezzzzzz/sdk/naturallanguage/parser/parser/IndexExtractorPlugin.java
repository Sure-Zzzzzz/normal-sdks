package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.NLParserConstant;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.TokenHelper;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 索引/表名提取插件
 * <p>
 * 支持的模式：
 * - "查user索引" / "查user这个索引"
 * - "从user表" / "在user表中"
 * - "user索引" / "user表"
 * - "user_behavior索引"（支持下划线和复合名称）
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(0)
public class IndexExtractorPlugin implements NLParserPlugin {

    @Override
    public boolean supports(IntentType intentType) {
        return true; // 索引提取适用于所有意图类型
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties properties,
                      ParseResult result) {
        String indexHint = extractAndRemove(tokens, keywordRegistry);
        if (indexHint != null) {
            result.setIndexHint(indexHint);
        }
    }

    /**
     * 提取索引/表名提示并从tokens列表中移除相关token
     */
    private String extractAndRemove(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        // 遍历查找索引指示词
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (!isIndexIndicator(token, keywordRegistry)) {
                continue;
            }

            IndexExtractionResult extractionResult = extractIndexName(tokens, i, keywordRegistry);
            if (extractionResult != null && extractionResult.indexName != null) {
                removeTokensByIndices(tokens, extractionResult.tokensToRemove);
                return extractionResult.indexName;
            }
        }

        return null;
    }

    private boolean isIndexIndicator(Token token, KeywordRegistry keywordRegistry) {
        return token != null &&
                token.getType() == TokenType.UNKNOWN &&
                keywordRegistry.isIndexIndicator(token.getText());
    }

    private IndexExtractionResult extractIndexName(List<Token> tokens, int indicatorIndex,
                                                    KeywordRegistry keywordRegistry) {
        IndexExtractionResult backwardResult = searchBackwardForIndexName(tokens, indicatorIndex, keywordRegistry);
        if (backwardResult != null) {
            return backwardResult;
        }
        return searchForwardForIndexName(tokens, indicatorIndex);
    }

    private IndexExtractionResult searchBackwardForIndexName(List<Token> tokens, int indicatorIndex,
                                                              KeywordRegistry keywordRegistry) {
        if (indicatorIndex <= 0) {
            return null;
        }

        // 从指示词向前搜索，只跳过紧邻的排除token（最多跳2个），收集连续的索引名token
        List<Integer> indexNameIndices = new ArrayList<>();
        List<Integer> skippedIndices = new ArrayList<>();
        int searchIndex = indicatorIndex - 1;
        int skipCount = 0;
        int maxSkip = 2;

        while (searchIndex >= 0) {
            Token token = tokens.get(searchIndex);

            // 跳过紧邻的排除token（停用词、动词、代词等），但限制跳过数量
            if (isExcludedFromIndexName(token, keywordRegistry)) {
                if (skipCount < maxSkip) {
                    skippedIndices.add(searchIndex);
                    skipCount++;
                    searchIndex--;
                    continue;
                } else {
                    // 超过最大跳过数，停止搜索
                    break;
                }
            }

            if (isValidIndexNameToken(token)) {
                indexNameIndices.add(0, searchIndex);
                searchIndex--;
                // 收集到有效索引名token后，重置skip计数（允许跳过索引名之间的间隔）
                skipCount = 0;
                continue;
            }

            // 遇到索引名填充词（如"这个"），视为边界
            if (isIndexNameFiller(token)) {
                break;
            }

            // 遇到其他非索引名token，停止
            break;
        }

        if (indexNameIndices.isEmpty()) {
            return null;
        }

        StringBuilder indexNameBuilder = new StringBuilder();
        for (Integer index : indexNameIndices) {
            indexNameBuilder.append(tokens.get(index).getText());
        }

        List<Integer> tokensToRemove = new ArrayList<>(indexNameIndices);
        tokensToRemove.add(indicatorIndex);
        tokensToRemove.addAll(skippedIndices);

        return new IndexExtractionResult(indexNameBuilder.toString(), tokensToRemove);
    }

    private IndexExtractionResult searchForwardForIndexName(List<Token> tokens, int indicatorIndex) {
        if (indicatorIndex >= tokens.size() - 1) {
            return null;
        }

        StringBuilder indexNameBuilder = new StringBuilder();
        List<Integer> indexNameIndices = new ArrayList<>();
        int searchIndex = indicatorIndex + 1;

        while (searchIndex < tokens.size()) {
            Token token = tokens.get(searchIndex);

            if (isValidIndexNameToken(token)) {
                indexNameIndices.add(searchIndex);
                searchIndex++;
                continue;
            }

            break;
        }

        if (indexNameIndices.isEmpty()) {
            return null;
        }

        for (Integer index : indexNameIndices) {
            indexNameBuilder.append(tokens.get(index).getText());
        }

        List<Integer> tokensToRemove = new ArrayList<>(indexNameIndices);
        tokensToRemove.add(indicatorIndex);

        return new IndexExtractionResult(indexNameBuilder.toString(), tokensToRemove);
    }

    private static final Set<String> EXCLUDED_CHINESE_TOKENS = new HashSet<>(Arrays.asList(
            "我", "你", "他", "她", "这个", "那个", "这些", "那些",
            "个", "下", "里",
            "查", "查询", "查找", "搜索",
            "看", "帮", "帮我",
            "从", "在", "的"
    ));

    private boolean isExcludedFromIndexName(Token token, KeywordRegistry keywordRegistry) {
        if (token == null) {
            return true;
        }
        String text = token.getText();

        // ASCII 标识符片段（字母/数字/下划线）不排除，避免 HanLP 拆分英文索引名时丢失片段
        if (text.matches("[a-zA-Z][a-zA-Z0-9_]*") || text.matches("[a-zA-Z0-9_]*[a-zA-Z][a-zA-Z0-9_]*")) {
            return false;
        }

        return keywordRegistry.isStopWord(text)
                || keywordRegistry.isPreposition(text)
                || keywordRegistry.isIndexIndicator(text)
                || keywordRegistry.resolveOperator(text) != null
                || keywordRegistry.resolveAggType(text) != null
                || keywordRegistry.isCollapseKeyword(text)
                || keywordRegistry.isPaginationKeyword(text)
                || keywordRegistry.resolveLogic(text) != null
                || keywordRegistry.resolveSortOrder(text) != null
                || EXCLUDED_CHINESE_TOKENS.contains(text);
    }

    private boolean isValidIndexNameToken(Token token) {
        if (token == null) {
            return false;
        }
        TokenType type = token.getType();
        // FIELD_CANDIDATE, NUMBER, 逻辑/操作符（如 user_behavior 中的下划线可能被拆分）都可以是索引名的一部分
        if (type == TokenType.FIELD_CANDIDATE || type == TokenType.NUMBER) {
            return true;
        }
        // UNKNOWN 类型：只有看起来像索引名片段的才有效（含英文字母、数字、下划线、中划线、点号）
        if (type == TokenType.UNKNOWN) {
            String text = token.getText();
            return text.matches(".*[a-zA-Z0-9_.\\-].*");
        }
        // LOGIC/OPERATOR 类型可能是索引名中的保留字（罕见），允许
        if (type == TokenType.LOGIC || type == TokenType.OPERATOR) {
            return true;
        }
        return false;
    }

    /**
     * 判断一个 UNKNOWN token 是否可能是索引名的前缀/后缀连接词（如 "这个"、"的" 等）
     * 这些词不应被收集进索引名
     */
    private boolean isIndexNameFiller(Token token) {
        if (token == null || token.getType() != TokenType.UNKNOWN) {
            return false;
        }
        String text = token.getText();
        // 纯中文且不含任何英文/数字/特殊字符的 token，不是索引名
        return text.matches("[\\u4e00-\\u9fa5]+") && !text.matches(".*[a-zA-Z0-9_.\\-].*");
    }

    private void removeTokensByIndices(List<Token> tokens, List<Integer> indicesToRemove) {
        TokenHelper.removeByIndices(tokens, indicesToRemove);
    }

    private static class IndexExtractionResult {
        final String indexName;
        final List<Integer> tokensToRemove;

        IndexExtractionResult(String indexName, List<Integer> tokensToRemove) {
            this.indexName = indexName;
            this.tokensToRemove = tokensToRemove;
        }
    }
}
