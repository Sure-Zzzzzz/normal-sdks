package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.NLParserKeywords;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.PaginationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 分页解析器
 * 支持多种分页表达方式：
 * 1. "返回/前/限制 10条" → limit=10
 * 2. "跳过 20条，返回 10条" → offset=20, limit=10
 * 3. "第3页，每页10条" → page=3, size=10, offset=20
 * 4. "从第21条开始，返回10条" → offset=20, limit=10
 * 5. "返回第21到30条" → offset=20, limit=10
 * 6. "继续查询，返回10条" → continueSearch=true, limit=10（用于ES search_after）
 * 7. "接着[value1,value2]继续查询" → continueSearch=true, searchAfter=[value1,value2]
 * <p>
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
public class PaginationParser {

    /**
     * 向前查找token的最大距离
     */
    private static final int MAX_LOOKAHEAD_DISTANCE = 3;

    /**
     * 范围表达式查找的最大距离
     */
    private static final int RANGE_LOOKAHEAD_DISTANCE = 5;

    /**
     * 分页相关硬编码关键词
     */
    private static final String KEYWORD_DI = "第";      // 第X页
    private static final String KEYWORD_YE = "页";      // 页
    private static final String KEYWORD_RETURN_CN = "返回";  // 返回
    private static final String KEYWORD_RETURN_EN = "return"; // return

    /**
     * search_after 游标值模式：接着[value1,value2]继续查询
     * 匹配方括号内的逗号分隔值
     */
    private static final Pattern SEARCH_AFTER_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    /**
     * 解析分页表达式
     *
     * @param tokens token列表
     * @return 分页意图，如果没有分页则返回null
     */
    public PaginationIntent parse(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        // 重构原始文本用于 search_after 模式匹配
        String fullText = reconstructText(tokens);

        Integer limit = null;
        Integer offset = null;
        Integer page = null;
        Integer size = null;
        Integer rangeStart = null;
        Integer rangeEnd = null;
        Boolean continueSearch = null;

        // 提取 search_after 游标值（如果有）
        List<Object> searchAfter = extractSearchAfter(fullText);

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() != TokenType.UNKNOWN && token.getType() != TokenType.NUMBER) {
                continue;
            }

            String text = token.getText();

            // 0. 识别 "继续查询" / "continue"（用于search_after）
            if (NLParserKeywords.isContinueKeyword(text)) {
                continueSearch = true;
                continue;
            }

            // 1. 识别 "跳过X条" / "skip X"
            if (NLParserKeywords.isOffsetKeyword(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    offset = num;
                }
            }

            // 2. 识别 "第X页" / "page X"
            else if (KEYWORD_DI.equals(text)) {
                // 查找"第"后面的数字，以及是否跟着"页"
                Integer num = findNextNumber(tokens, i);
                if (num != null && isFollowedByKeyword(tokens, i, KEYWORD_YE)) {
                    page = num;
                }
                // 还可能是"第X条"，用于范围表达
                else if (num != null && !isFollowedByKeyword(tokens, i, KEYWORD_YE)) {
                    // 检查后面是否有"到"/"至"等范围词
                    int nextIdx = findNextTokenIndex(tokens, i, TokenType.UNKNOWN);
                    if (nextIdx != -1 && NLParserKeywords.isRangeKeyword(tokens.get(nextIdx).getText())) {
                        rangeStart = num;
                        // 查找范围结束
                        Integer endNum = findNextNumber(tokens, nextIdx);
                        if (endNum != null) {
                            rangeEnd = endNum;
                        }
                    }
                }
            }

            // 3. 识别 "每页X条" / "size X"
            else if (NLParserKeywords.isSizeKeyword(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    size = num;
                }
            }

            // 4. 识别 "从第X条开始" / "from X"
            else if (NLParserKeywords.isFromKeyword(text)) {
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    offset = num - 1; // "从第21条" = 跳过前20条
                }
            }

            // 5. 识别 "返回/前/限制/limit X条"
            else if (NLParserKeywords.isLimitKeyword(text)) {
                // 特殊处理"返回第X到Y条"的情况
                if (KEYWORD_RETURN_CN.equals(text) || KEYWORD_RETURN_EN.equalsIgnoreCase(text)) {
                    // 先检查是否是范围表达
                    int nextIdx = findNextTokenIndex(tokens, i, TokenType.UNKNOWN);
                    if (nextIdx != -1 && KEYWORD_DI.equals(tokens.get(nextIdx).getText())) {
                        Integer startNum = findNextNumber(tokens, nextIdx);
                        if (startNum != null) {
                            int rangeIdx = findNextTokenIndex(tokens, nextIdx + 1, TokenType.UNKNOWN);
                            if (rangeIdx != -1 && NLParserKeywords.isRangeKeyword(tokens.get(rangeIdx).getText())) {
                                rangeStart = startNum;
                                Integer endNum = findNextNumber(tokens, rangeIdx);
                                if (endNum != null) {
                                    rangeEnd = endNum;
                                }
                                continue; // 跳过后续的limit解析
                            }
                        }
                    }
                }

                // 常规limit解析
                Integer num = findNextNumber(tokens, i);
                if (num != null) {
                    limit = num;
                }
            }
        }

        // 处理范围表达：第X到Y条 → offset = X-1, limit = Y-X+1
        if (rangeStart != null && rangeEnd != null) {
            offset = rangeStart - 1;
            limit = rangeEnd - rangeStart + 1;
        }

        // 如果有page和size，计算offset
        if (page != null && size != null) {
            offset = (page - 1) * size;
            limit = size; // size也作为limit
        }

        // 如果只有page没有size，但有limit，则size=limit
        if (page != null && size == null && limit != null) {
            size = limit;
            offset = (page - 1) * size;
        }

        // 至少有一个分页参数才返回
        if (limit != null || offset != null || page != null || size != null || continueSearch != null || searchAfter != null) {
            return PaginationIntent.builder()
                    .limit(limit)
                    .offset(offset)
                    .page(page)
                    .size(size)
                    .continueSearch(continueSearch)
                    .searchAfter(searchAfter)
                    .build();
        }

        return null;
    }

    /**
     * 查找下一个数字token的值
     */
    private Integer findNextNumber(List<Token> tokens, int fromIndex) {
        int maxIndex = Math.min(tokens.size(), fromIndex + MAX_LOOKAHEAD_DISTANCE + 1);

        for (int i = fromIndex + 1; i < maxIndex; i++) {
            Token token = tokens.get(i);
            if (token.getType() == TokenType.NUMBER) {
                return ((Number) token.getValue()).intValue();
            }
            // 如果是UNKNOWN但能解析为数字
            if (token.getType() == TokenType.UNKNOWN) {
                try {
                    return Integer.parseInt(token.getText());
                } catch (NumberFormatException e) {
                    // 继续查找
                }
            }
        }
        return null;
    }

    /**
     * 判断某个索引后面是否紧跟着特定关键词
     */
    private boolean isFollowedByKeyword(List<Token> tokens, int fromIndex, String keyword) {
        int maxIndex = Math.min(tokens.size(), fromIndex + MAX_LOOKAHEAD_DISTANCE + 1);

        for (int i = fromIndex + 1; i < maxIndex; i++) {
            Token token = tokens.get(i);
            if (token.getType() == TokenType.UNKNOWN && token.getText().equals(keyword)) {
                return true;
            }
            // 跳过数字继续查找
            if (token.getType() != TokenType.NUMBER) {
                break;
            }
        }
        return false;
    }

    /**
     * 查找下一个指定类型的token索引
     */
    private int findNextTokenIndex(List<Token> tokens, int fromIndex, TokenType type) {
        int maxIndex = Math.min(tokens.size(), fromIndex + RANGE_LOOKAHEAD_DISTANCE + 1);

        for (int i = fromIndex + 1; i < maxIndex; i++) {
            Token token = tokens.get(i);
            if (token.getType() == type) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 重构原始文本
     * 将 token 列表拼接回文本，用于模式匹配
     */
    private String reconstructText(List<Token> tokens) {
        return tokens.stream()
                .map(Token::getText)
                .collect(Collectors.joining());
    }

    /**
     * 提取 search_after 游标值
     * 支持格式：接着[value1,value2]继续查询
     *
     * @param text 原始文本
     * @return search_after 值列表，如果没有则返回null
     */
    private List<Object> extractSearchAfter(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher matcher = SEARCH_AFTER_PATTERN.matcher(text);
        if (matcher.find()) {
            String bracketContent = matcher.group(1); // 方括号内的内容
            if (bracketContent != null && !bracketContent.trim().isEmpty()) {
                // 按逗号分割
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

    /**
     * 智能解析值类型
     * 尝试解析为数字（Long/Double），失败则保持字符串
     *
     * @param value 待解析的值
     * @return 解析后的对象（Long/Double/String）
     */
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
