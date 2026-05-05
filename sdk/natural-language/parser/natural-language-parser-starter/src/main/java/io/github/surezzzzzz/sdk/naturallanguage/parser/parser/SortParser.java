package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.NLParserConstant;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.SortIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.TokenHelper;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * 排序解析插件
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(40)
public class SortParser implements NLParserPlugin {

    private static final int MAX_FIELD_LOOKAHEAD_DISTANCE = 5;

    @Override
    public boolean supports(IntentType intentType) {
        return intentType == IntentType.QUERY;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        List<SortIntent> sorts = doParse(tokens, keywordRegistry);
        if (sorts != null && !sorts.isEmpty()) {
            result.setSorts(sorts);
        }
    }

    private List<SortIntent> doParse(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) {
            return new ArrayList<>();
        }

        List<SortIntent> sorts = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() == TokenType.SORT) {
                SortIntent sort = parseSort(tokens, i, keywordRegistry);
                if (sort != null) {
                    sorts.add(sort);
                }
            }
        }

        return sorts;
    }

    private SortIntent parseSort(List<Token> tokens, int sortTokenIndex, KeywordRegistry keywordRegistry) {
        Token sortToken = tokens.get(sortTokenIndex);
        String fieldHint = findSortField(tokens, sortTokenIndex, keywordRegistry);

        if (fieldHint != null && !fieldHint.isEmpty()) {
            return SortIntent.builder()
                    .fieldHint(fieldHint)
                    .order(sortToken.getSortOrder())
                    .build();
        }

        return null;
    }

    private String findSortField(List<Token> tokens, int sortTokenIndex, KeywordRegistry keywordRegistry) {
        List<String> fieldParts = new ArrayList<>();
        int startIndex = Math.max(0, sortTokenIndex - MAX_FIELD_LOOKAHEAD_DISTANCE);

        for (int j = sortTokenIndex - 1; j >= startIndex; j--) {
            Token prevToken = tokens.get(j);

            if (isBoundaryToken(prevToken)) {
                break;
            }

            if (prevToken.getType() == TokenType.UNKNOWN ||
                    prevToken.getType() == TokenType.FIELD_CANDIDATE) {
                if (keywordRegistry.isPreposition(prevToken.getText())) {
                    break;
                }
                fieldParts.add(0, prevToken.getText());
            }
        }

        return fieldParts.isEmpty() ? null : String.join("", fieldParts);
    }

    private boolean isBoundaryToken(Token token) {
        return TokenHelper.isBoundaryToken(token);
    }
}
