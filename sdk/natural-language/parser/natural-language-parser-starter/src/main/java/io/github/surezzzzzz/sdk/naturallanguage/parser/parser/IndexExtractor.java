package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.NLParserKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.List;

/**
 * 索引/表名提取器
 * 支持的模式：
 * - "查user索引" / "查user这个索引"
 * - "从user表" / "在user表中"
 * - "user索引" / "user表"
 * <p>
 * 线程安全：无状态，线程安全
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
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() == TokenType.UNKNOWN) {
                String text = token.getText();

                // 检查是否为索引/表名标识关键词
                if (NLParserKeywords.isIndexIndicator(text)) {
                    // 向前查找索引名，跳过"这个"、"那个"等指示词
                    String indexName = searchBackwardForIndexName(tokens, i);
                    if (indexName != null) {
                        return indexName;
                    }

                    // 向后查找索引名（可能在关键词后面）
                    String indexNameAfter = searchForwardForIndexName(tokens, i);
                    if (indexNameAfter != null) {
                        return indexNameAfter;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 向前查找索引名
     */
    private String searchBackwardForIndexName(List<Token> tokens, int keywordIndex) {
        int searchIndex = keywordIndex - 1;
        Token indicatorToken = null;  // 记录"这个"之类的指示词token

        while (searchIndex >= 0) {
            Token prevToken = tokens.get(searchIndex);

            if (prevToken.getType() == TokenType.UNKNOWN) {
                String prevText = prevToken.getText();

                // 如果是指示词，记录并继续向前找
                if (NLParserKeywords.isDemonstrativeWord(prevText)) {
                    indicatorToken = prevToken;
                    searchIndex--;
                    continue;
                }

                // 找到了索引名
                String indexName = prevText;

                // 移除相关tokens（从后往前移除，避免索引变化）
                tokens.remove(keywordIndex);  // 移除"索引"/"表"
                if (indicatorToken != null) {
                    tokens.remove(tokens.indexOf(indicatorToken));  // 移除"这个"等
                }
                tokens.remove(searchIndex);  // 移除索引名

                return indexName;
            }

            // 遇到非UNKNOWN token，停止查找
            break;
        }

        return null;
    }

    /**
     * 向后查找索引名
     */
    private String searchForwardForIndexName(List<Token> tokens, int keywordIndex) {
        if (keywordIndex + 1 < tokens.size()) {
            Token nextToken = tokens.get(keywordIndex + 1);
            if (nextToken.getType() == TokenType.UNKNOWN) {
                String indexName = nextToken.getText();
                // 移除"索引"/"表"关键词token
                tokens.remove(keywordIndex);
                // 移除索引名token（注意索引已经变化）
                tokens.remove(keywordIndex);
                return indexName;
            }
        }
        return null;
    }
}
