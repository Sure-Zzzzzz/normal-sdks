package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.PaginationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import org.springframework.core.annotation.Order;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 分页解析插件
 *
 * @author surezzzzzz
 */
@NaturalLanguageParserComponent
@Order(30)
public class PaginationParser implements NLParserPlugin {

    private static final int MAX_LOOKAHEAD_DISTANCE = 3;
    private static final int RANGE_LOOKAHEAD_DISTANCE = 5;

    // 分页子类型关键词（parser 内部实现细节，不需要放进 KeywordRegistry）
    private static final Set<String> LIMIT_KEYWORDS = new HashSet<>(Arrays.asList(
            "限制", "最多", "前", "返回", "取", "limit"
    ));
    private static final Set<String> OFFSET_KEYWORDS = new HashSet<>(Arrays.asList(
            "跳过", "跳过前", "忽略", "忽略前", "skip", "offset"
    ));
    private static final Set<String> SIZE_KEYWORDS = new HashSet<>(Arrays.asList(
            "每页", "每", "size"
    ));
    private static final Set<String> FROM_KEYWORDS = new HashSet<>(Arrays.asList(
            "从", "从第", "起始", "from"
    ));
    private static final Set<String> CONTINUE_KEYWORDS = new HashSet<>(Arrays.asList(
            "继续", "继续查询", "接着", "接着查", "下一页", "下一", "continue"
    ));
    private static final Set<String> RANGE_KEYWORDS = new HashSet<>(Arrays.asList(
            "到", "至", "到第", "至第", "~", "-"
    ));

    private static final String KEYWORD_DI = "第";
    private static final String KEYWORD_YE = "页";
    private static final String KEYWORD_RETURN_CN = "返回";

    private static final Pattern SEARCH_AFTER_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    @Override
    public boolean supports(IntentType intentType) {
        return intentType == IntentType.QUERY;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        PaginationIntent pagination = doParse(tokens, keywordRegistry);
        if (pagination != null) {
            result.setPagination(pagination);
        }
    }

    private PaginationIntent doParse(List<Token> tokens, KeywordRegistry keywordRegistry) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        String fullText = reconstructText(tokens);

        Integer limit = null;
        Integer offset = null;
        Integer page = null;
        Integer size = null;
        Integer rangeStart = null;
        Integer rangeEnd = null;
        Boolean continueSearch = null;

        List<Object> searchAfter = extractSearchAfter(fullText);

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() != TokenType.UNKNOWN && token.getType() != TokenType.NUMBER &&
                    token.getType() != TokenType.PAGINATION) {
                continue;
            }

            String text = token.getText();

            if (isContinueKeyword(text)) {
                continueSearch = true;
                continue;
            }

            if (isOffsetKeyword(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    offset = num;
                }
            } else if (KEYWORD_DI.equals(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null && isFollowedByKeyword(tokens, i, KEYWORD_YE)) {
                    page = num;
                } else if (num != null && !isFollowedByKeyword(tokens, i, KEYWORD_YE)) {
                    int nextIdx = findNextTokenIndex(tokens, i, TokenType.UNKNOWN);
                    if (nextIdx != -1 && isRangeKeyword(tokens.get(nextIdx).getText())) {
                        rangeStart = num;
                        Integer endNum = findNextNumber(tokens, nextIdx);
                        if (endNum != null) {
                            rangeEnd = endNum;
                        }
                    }
                }
            } else if (isSizeKeyword(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    size = num;
                }
            } else if (isFromKeyword(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    offset = num - 1;
                }
            } else if (isLimitKeyword(text)) {
                if (KEYWORD_RETURN_CN.equals(text)) {
                    int nextIdx = findNextTokenIndex(tokens, i, TokenType.UNKNOWN);
                    if (nextIdx != -1 && KEYWORD_DI.equals(tokens.get(nextIdx).getText())) {
                        Integer startNum = findNextNumber(tokens, nextIdx);
                        if (startNum != null) {
                            int rangeIdx = findNextTokenIndex(tokens, nextIdx + 1, TokenType.UNKNOWN);
                            if (rangeIdx != -1 && isRangeKeyword(tokens.get(rangeIdx).getText())) {
                                rangeStart = startNum;
                                Integer endNum = findNextNumber(tokens, rangeIdx);
                                if (endNum != null) {
                                    rangeEnd = endNum;
                                }
                                continue;
                            }
                        }
                    }
                }

                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    limit = num;
                }
            }
        }

        if (rangeStart != null && rangeEnd != null) {
            offset = rangeStart - 1;
            limit = rangeEnd - rangeStart + 1;
        }

        if (page != null && size != null) {
            offset = (page - 1) * size;
            limit = size;
        }

        if (page != null && size == null && limit != null) {
            size = limit;
            offset = (page - 1) * size;
        }

        if (limit != null || offset != null || page != null || size != null || continueSearch != null || searchAfter != null) {
            PaginationIntent.PaginationIntentBuilder builder = PaginationIntent.builder()
                    .page(page)
                    .size(size != null ? size : limit)
                    .offset(offset != null ? offset.longValue() : null)
                    .searchAfter(searchAfter);

            if (searchAfter != null || continueSearch != null) {
                builder.type(PaginationType.SEARCH_AFTER);
                if (continueSearch != null && continueSearch) {
                    builder.searchAfterMode(SearchAfterMode.TIEBREAKER);
                }
            } else {
                builder.type(PaginationType.OFFSET);
            }

            return builder.build();
        }

        return null;
    }

    private boolean isLimitKeyword(String text) {
        return isKeywordInSet(text, LIMIT_KEYWORDS);
    }

    private boolean isOffsetKeyword(String text) {
        return isKeywordInSet(text, OFFSET_KEYWORDS);
    }

    private boolean isSizeKeyword(String text) {
        return isKeywordInSet(text, SIZE_KEYWORDS);
    }

    private boolean isFromKeyword(String text) {
        return isKeywordInSet(text, FROM_KEYWORDS);
    }

    private boolean isContinueKeyword(String text) {
        return isKeywordInSet(text, CONTINUE_KEYWORDS);
    }

    private boolean isRangeKeyword(String text) {
        return isKeywordInSet(text, RANGE_KEYWORDS);
    }

    private static boolean isKeywordInSet(String text, Set<String> keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (kw.equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    private Integer findNextNumber(List<Token> tokens, int fromIndex) {
        int maxIndex = Math.min(tokens.size(), fromIndex + MAX_LOOKAHEAD_DISTANCE + 1);
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            Token token = tokens.get(i);
            if (token.getType() == TokenType.NUMBER) {
                return ((Number) token.getValue()).intValue();
            }
            if (token.getType() == TokenType.UNKNOWN) {
                try {
                    return Integer.parseInt(token.getText());
                } catch (NumberFormatException e) {
                    // continue
                }
            }
        }
        return null;
    }

    private boolean isFollowedByKeyword(List<Token> tokens, int fromIndex, String keyword) {
        int maxIndex = Math.min(tokens.size(), fromIndex + MAX_LOOKAHEAD_DISTANCE + 1);
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            Token token = tokens.get(i);
            if (token.getType() == TokenType.UNKNOWN && token.getText().equals(keyword)) {
                return true;
            }
            if (token.getType() != TokenType.NUMBER) {
                break;
            }
        }
        return false;
    }

    private int findNextTokenIndex(List<Token> tokens, int fromIndex, TokenType type) {
        int maxIndex = Math.min(tokens.size(), fromIndex + RANGE_LOOKAHEAD_DISTANCE + 1);
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if (tokens.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    private String reconstructText(List<Token> tokens) {
        return tokens.stream().map(Token::getText).collect(Collectors.joining());
    }

    private List<Object> extractSearchAfter(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = SEARCH_AFTER_PATTERN.matcher(text);
        if (matcher.find()) {
            String bracketContent = matcher.group(1);
            if (bracketContent != null && !bracketContent.trim().isEmpty()) {
                String[] values = bracketContent.split(",");
                List<Object> result = new ArrayList<>();
                for (String value : values) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(parseValue(trimmed));
                    }
                }
                return result.isEmpty() ? null : result;
            }
        }
        return null;
    }

    private Object parseValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e1) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e2) {
                return value;
            }
        }
    }
}
