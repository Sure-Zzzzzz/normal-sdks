package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.NLParserConstant;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.CollapseIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.TokenHelper;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段折叠（去重）解析插件
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(50)
public class CollapseParser implements NLParserPlugin {

    @Override
    public boolean supports(IntentType intentType) {
        return intentType == IntentType.QUERY;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        CollapseIntent collapse = doParse(tokens, keywordRegistry);
        if (collapse != null) {
            result.setCollapse(collapse);
        }
    }

    private CollapseIntent doParse(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // 检查 COLLAPSE 类型 token
            if (token.getType() == TokenType.COLLAPSE) {
                CollapseIntent collapse = parseCollapse(tokens, i, keywordRegistry);
                if (collapse != null) {
                    return collapse;
                }
            }

            // 检查连续两个 UNKNOWN token 的组合
            if (i + 1 < tokens.size()) {
                Token nextToken = tokens.get(i + 1);
                if (token.getType() == TokenType.UNKNOWN &&
                        nextToken.getType() == TokenType.UNKNOWN) {
                    String combined = token.getText() + nextToken.getText();
                    if (keywordRegistry.isCollapseKeyword(combined)) {
                        CollapseIntent collapse = parseCombinedCollapse(tokens, i, i + 1, keywordRegistry);
                        if (collapse != null) {
                            return collapse;
                        }
                    }
                }
            }

            // 也检查单个 UNKNOWN token 是否为折叠词
            if (token.getType() == TokenType.UNKNOWN &&
                    keywordRegistry.isCollapseKeyword(token.getText())) {
                CollapseIntent collapse = parseCollapse(tokens, i, keywordRegistry);
                if (collapse != null) {
                    return collapse;
                }
            }
        }

        return null;
    }

    private CollapseIntent parseCollapse(List<Token> tokens, int collapseTokenIndex, KeywordRegistry keywordRegistry) {
        String fieldHint = findFieldBefore(tokens, collapseTokenIndex, keywordRegistry);
        if (fieldHint == null || fieldHint.isEmpty()) {
            fieldHint = findFieldAfter(tokens, collapseTokenIndex);
        }

        if (fieldHint != null && !fieldHint.isEmpty()) {
            return CollapseIntent.builder()
                    .fieldHint(fieldHint)
                    .build();
        }

        return null;
    }

    private CollapseIntent parseCombinedCollapse(List<Token> tokens, int firstTokenIndex, int lastTokenIndex,
                                                 KeywordRegistry keywordRegistry) {
        String fieldHint = findFieldBefore(tokens, firstTokenIndex, keywordRegistry);
        if (fieldHint == null || fieldHint.isEmpty()) {
            fieldHint = findFieldAfter(tokens, lastTokenIndex);
        }

        if (fieldHint != null && !fieldHint.isEmpty()) {
            return CollapseIntent.builder()
                    .fieldHint(fieldHint)
                    .build();
        }

        return null;
    }

    private String findFieldBefore(List<Token> tokens, int collapseTokenIndex, KeywordRegistry keywordRegistry) {
        List<String> fieldParts = new ArrayList<>();
        int startIndex = Math.max(0, collapseTokenIndex - NLParserConstant.MAX_FIELD_LOOKAHEAD_DISTANCE);

        for (int j = collapseTokenIndex - 1; j >= startIndex; j--) {
            Token prevToken = tokens.get(j);

            if (isBoundaryToken(prevToken)) {
                break;
            }

            if (keywordRegistry.isPreposition(prevToken.getText())) {
                continue;
            }

            if (prevToken.getType() == TokenType.UNKNOWN ||
                    prevToken.getType() == TokenType.FIELD_CANDIDATE) {
                fieldParts.add(0, prevToken.getText());
            }
        }

        return fieldParts.isEmpty() ? null : String.join("", fieldParts);
    }

    private String findFieldAfter(List<Token> tokens, int collapseTokenIndex) {
        List<String> fieldParts = new ArrayList<>();
        int endIndex = Math.min(tokens.size(), collapseTokenIndex + NLParserConstant.MAX_FIELD_LOOKBEHIND_DISTANCE + 1);

        for (int j = collapseTokenIndex + 1; j < endIndex; j++) {
            Token nextToken = tokens.get(j);

            if (isBoundaryToken(nextToken)) {
                break;
            }

            if (nextToken.getType() == TokenType.UNKNOWN ||
                    nextToken.getType() == TokenType.FIELD_CANDIDATE) {
                fieldParts.add(nextToken.getText());
            }
        }

        return fieldParts.isEmpty() ? null : String.join("", fieldParts);
    }

    private boolean isBoundaryToken(Token token) {
        return TokenHelper.isBoundaryToken(token);
    }
}
