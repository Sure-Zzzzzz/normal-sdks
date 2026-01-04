package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.model.DateRangeIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间范围解析器
 * <p>
 * 支持的表达方式：
 * - "时间范围2025-01-01到2026-01-01"
 * - "日期范围2025-01-01至2026-01-01"
 * - "dateRange 2025-01-01 ~ 2026-01-01"
 * <p>
 * 支持的日期格式：
 * - ISO 8601: YYYY-MM-DDTHH:mm:ss, YYYY-MM-DDTHH:mm
 * - 空格分隔: YYYY-MM-DD HH:mm:ss, YYYY-MM-DD HH:mm
 * - 斜杠格式: YYYY/MM/DD HH:mm:ss, YYYY/MM/DD HH:mm
 * - 中文格式: YYYY年MM月DD日 HH时mm分ss秒
 * - 紧凑格式: YYYYMMDD HHmmss, YYYYMMDD HHmm
 * - 纯日期: YYYY-MM-DD, YYYY/MM/DD, YYYY年MM月DD日, YYYYMMDD
 * <p>
 * 注意：
 * - 只提取带有明确"时间范围"/"日期范围"关键词的表达
 * - 其他时间条件（如"创建时间2025-01-01到2026-01-01"）由ConditionParser处理
 * <p>
 * 线程安全：无状态，线程安全
 *
 * @author surezzzzzz
 */
@Slf4j
public class DateRangeParser {

    // ==================== 常量定义 ====================

    /**
     * 时间范围关键词（只包含明确的范围关键词）
     */
    private static final Set<String> DATE_RANGE_KEYWORDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "时间范围", "日期范围",
                    "dateRange", "date range", "time range", "timeRange",
                    "date_range", "time_range"
            ))
    );

    /**
     * 范围分隔词
     */
    private static final Set<String> RANGE_SEPARATORS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("到", "至", "~", "-"))
    );

    /**
     * 日期格式正则表达式（按优先级排序，先匹配完整格式）
     */
    private static final List<DatePattern> DATE_PATTERNS = initDatePatterns();

    // ==================== 初始化 ====================

    /**
     * 初始化日期格式列表
     */
    private static List<DatePattern> initDatePatterns() {
        List<DatePattern> patterns = new ArrayList<>();

        // ISO 8601格式
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})T(\\d{1,2}):(\\d{1,2}):(\\d{1,2})",
                DateFormatType.ISO_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})T(\\d{1,2}):(\\d{1,2})",
                DateFormatType.ISO_WITH_MINUTES
        ));

        // 空格分隔格式
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})",
                DateFormatType.STANDARD_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})",
                DateFormatType.STANDARD_WITH_MINUTES
        ));

        // 无空格格式（tokenizer可能吞掉空格）
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})(\\d{1,2}):(\\d{1,2}):(\\d{1,2})",
                DateFormatType.COMPACT_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})(\\d{1,2}):(\\d{1,2})",
                DateFormatType.COMPACT_WITH_MINUTES
        ));

        // 斜杠格式
        patterns.add(new DatePattern(
                "(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})",
                DateFormatType.SLASH_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})",
                DateFormatType.SLASH_WITH_MINUTES
        ));
        patterns.add(new DatePattern(
                "(\\d{4})/(\\d{1,2})/(\\d{1,2})(\\d{1,2}):(\\d{1,2}):(\\d{1,2})",
                DateFormatType.SLASH_COMPACT_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})/(\\d{1,2})/(\\d{1,2})(\\d{1,2}):(\\d{1,2})",
                DateFormatType.SLASH_COMPACT_WITH_MINUTES
        ));

        // 中文格式
        patterns.add(new DatePattern(
                "(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})时(\\d{1,2})分(\\d{1,2})秒",
                DateFormatType.CHINESE_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})时(\\d{1,2})分",
                DateFormatType.CHINESE_WITH_MINUTES
        ));
        patterns.add(new DatePattern(
                "(\\d{4})年(\\d{1,2})月(\\d{1,2})日(\\d{1,2})时(\\d{1,2})分(\\d{1,2})秒",
                DateFormatType.CHINESE_COMPACT_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{4})年(\\d{1,2})月(\\d{1,2})日(\\d{1,2})时(\\d{1,2})分",
                DateFormatType.CHINESE_COMPACT_WITH_MINUTES
        ));

        // 紧凑数字格式
        patterns.add(new DatePattern(
                "(\\d{8})\\s+(\\d{6})",
                DateFormatType.NUMERIC_WITH_SECONDS
        ));
        patterns.add(new DatePattern(
                "(\\d{8})\\s+(\\d{4})",
                DateFormatType.NUMERIC_WITH_MINUTES
        ));

        // 纯日期格式
        patterns.add(new DatePattern(
                "(\\d{4})-(\\d{1,2})-(\\d{1,2})",
                DateFormatType.DATE_ONLY_DASH
        ));
        patterns.add(new DatePattern(
                "(\\d{4})/(\\d{1,2})/(\\d{1,2})",
                DateFormatType.DATE_ONLY_SLASH
        ));
        patterns.add(new DatePattern(
                "(\\d{4})年(\\d{1,2})月(\\d{1,2})日",
                DateFormatType.DATE_ONLY_CHINESE
        ));
        patterns.add(new DatePattern(
                "(\\d{8})",
                DateFormatType.DATE_ONLY_NUMERIC
        ));

        return Collections.unmodifiableList(patterns);
    }

    // ==================== 公共方法 ====================

    /**
     * 解析时间范围表达式
     *
     * @param tokens token列表（会被修改，移除已识别的时间范围相关token）
     * @return 时间范围意图，如果没有时间范围则返回null
     */
    public DateRangeIntent parse(List<Token> tokens) {
        // 参数验证
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        // 拼接tokens为文本
        String fullText = joinTokens(tokens);
        if (fullText.isEmpty()) {
            return null;
        }

        // 提取时间范围
        DateRangeExtractionResult result = extractDateRange(fullText, tokens);
        if (result == null || result.dateRange == null) {
            return null;
        }

        // 移除已识别的tokens
        removeTokensByIndices(tokens, result.tokensToRemove);

        return result.dateRange;
    }

    // ==================== 文本拼接 ====================

    /**
     * 将tokens拼接成字符串
     */
    private String joinTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            sb.append(token.getText());
        }
        return sb.toString();
    }

    // ==================== 时间范围提取 ====================

    /**
     * 提取时间范围
     * 只提取带有明确"时间范围"/"日期范围"关键词的表达
     */
    private DateRangeExtractionResult extractDateRange(String fullText, List<Token> tokens) {
        String textLower = fullText.toLowerCase();

        // 查找时间范围关键词
        for (String keyword : DATE_RANGE_KEYWORDS) {
            String keywordLower = keyword.toLowerCase();
            int keywordIndex = textLower.indexOf(keywordLower);

            if (keywordIndex == -1) {
                continue;
            }

            // 提取关键词后的文本
            int afterIndex = keywordIndex + keyword.length();
            String afterKeyword = fullText.substring(afterIndex);

            // 查找日期范围
            DateRangeIntent range = findDateRangeInText(afterKeyword);
            if (range != null) {
                range.setFieldHint(keyword);

                // 找出需要移除的token索引
                List<Integer> tokensToRemove = findTokenIndicesInRange(
                        tokens, fullText, keywordIndex, afterKeyword
                );

                return new DateRangeExtractionResult(range, tokensToRemove);
            }
        }

        return null;
    }

    /**
     * 在文本中查找日期范围
     */
    private DateRangeIntent findDateRangeInText(String text) {
        // 提取所有日期
        List<String> dates = extractAllDates(text);
        if (dates.size() < 2) {
            return null;
        }

        // 检查是否有范围分隔词
        if (!containsRangeSeparator(text)) {
            return null;
        }

        // 构建日期范围（取前两个日期）
        return buildDateRange(dates.get(0), dates.get(1));
    }

    /**
     * 检查文本是否包含范围分隔词
     */
    private boolean containsRangeSeparator(String text) {
        for (String separator : RANGE_SEPARATORS) {
            if (text.contains(separator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建日期范围
     */
    private DateRangeIntent buildDateRange(String fromDate, String toDate) {
        String fromISO = convertToISO(fromDate);
        String toISO = convertToISO(toDate);

        if (fromISO == null || toISO == null) {
            return null;
        }

        return DateRangeIntent.builder()
                .from(fromISO)
                .to(toISO)
                .build();
    }

    // ==================== 日期提取 ====================

    /**
     * 从文本中提取所有日期
     * 优先提取最长匹配，避免重复
     */
    private List<String> extractAllDates(String text) {
        // 收集所有匹配
        List<DateMatch> matches = collectDateMatches(text);

        // 排序并去重
        return deduplicateDateMatches(matches);
    }

    /**
     * 收集所有日期匹配
     */
    private List<DateMatch> collectDateMatches(String text) {
        List<DateMatch> matches = new ArrayList<>();

        for (DatePattern datePattern : DATE_PATTERNS) {
            Matcher matcher = datePattern.pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new DateMatch(
                        matcher.group(),
                        matcher.start(),
                        matcher.end(),
                        datePattern.type
                ));
            }
        }

        return matches;
    }

    /**
     * 去重日期匹配（保留最长的）
     */
    private List<String> deduplicateDateMatches(List<DateMatch> matches) {
        // 按位置排序，相同位置的按长度降序
        Collections.sort(matches, new Comparator<DateMatch>() {
            @Override
            public int compare(DateMatch a, DateMatch b) {
                if (a.start != b.start) {
                    return Integer.compare(a.start, b.start);
                }
                return Integer.compare(b.text.length(), a.text.length());
            }
        });

        // 去除重叠的匹配
        List<String> dates = new ArrayList<>();
        int lastEnd = -1;

        for (DateMatch match : matches) {
            if (match.start >= lastEnd) {
                dates.add(match.text);
                lastEnd = match.end;
            }
        }

        return dates;
    }

    // ==================== 日期转换 ====================

    /**
     * 将日期字符串转换为ISO 8601格式
     */
    private String convertToISO(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // 尝试匹配各种格式
        for (DatePattern datePattern : DATE_PATTERNS) {
            Matcher matcher = datePattern.pattern.matcher(dateStr);
            if (matcher.find()) {
                try {
                    LocalDateTime dateTime = parseDateTime(matcher, datePattern.type);
                    if (dateTime != null) {
                        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse date: {}", dateStr, e);
                }
            }
        }

        return null;
    }

    /**
     * 根据格式类型解析日期时间（Java 8版本）
     */
    private LocalDateTime parseDateTime(Matcher matcher, DateFormatType formatType) {
        try {
            // 使用传统的switch语句
            switch (formatType) {
                case ISO_WITH_SECONDS:
                case STANDARD_WITH_SECONDS:
                case COMPACT_WITH_SECONDS:
                case SLASH_WITH_SECONDS:
                case SLASH_COMPACT_WITH_SECONDS:
                case CHINESE_WITH_SECONDS:
                case CHINESE_COMPACT_WITH_SECONDS:
                    return parseWithSeconds(matcher);

                case ISO_WITH_MINUTES:
                case STANDARD_WITH_MINUTES:
                case COMPACT_WITH_MINUTES:
                case SLASH_WITH_MINUTES:
                case SLASH_COMPACT_WITH_MINUTES:
                case CHINESE_WITH_MINUTES:
                case CHINESE_COMPACT_WITH_MINUTES:
                    return parseWithMinutes(matcher);

                case NUMERIC_WITH_SECONDS:
                    return parseNumericWithSeconds(matcher);

                case NUMERIC_WITH_MINUTES:
                    return parseNumericWithMinutes(matcher);

                case DATE_ONLY_DASH:
                case DATE_ONLY_SLASH:
                case DATE_ONLY_CHINESE:
                    return parseDateOnly(matcher);

                case DATE_ONLY_NUMERIC:
                    return parseNumericDateOnly(matcher);

                default:
                    return null;
            }
        } catch (Exception e) {
            log.debug("Failed to parse datetime: {}", matcher.group(), e);
            return null;
        }
    }

    /**
     * 解析带时分秒的格式（6个捕获组）
     */
    private LocalDateTime parseWithSeconds(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        int second = Integer.parseInt(matcher.group(6));
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    /**
     * 解析带时分的格式（5个捕获组）
     */
    private LocalDateTime parseWithMinutes(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        return LocalDateTime.of(year, month, day, hour, minute, 0);
    }

    /**
     * 解析纯日期格式（3个捕获组）
     */
    private LocalDateTime parseDateOnly(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        return LocalDateTime.of(year, month, day, 0, 0, 0);
    }

    /**
     * 解析数字格式的日期时间（YYYYMMDD HHmmss）
     */
    private LocalDateTime parseNumericWithSeconds(Matcher matcher) {
        String datePart = matcher.group(1); // YYYYMMDD
        String timePart = matcher.group(2); // HHmmss

        int year = Integer.parseInt(datePart.substring(0, 4));
        int month = Integer.parseInt(datePart.substring(4, 6));
        int day = Integer.parseInt(datePart.substring(6, 8));
        int hour = Integer.parseInt(timePart.substring(0, 2));
        int minute = Integer.parseInt(timePart.substring(2, 4));
        int second = Integer.parseInt(timePart.substring(4, 6));

        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    /**
     * 解析数字格式的日期时间（YYYYMMDD HHmm）
     */
    private LocalDateTime parseNumericWithMinutes(Matcher matcher) {
        String datePart = matcher.group(1); // YYYYMMDD
        String timePart = matcher.group(2); // HHmm

        int year = Integer.parseInt(datePart.substring(0, 4));
        int month = Integer.parseInt(datePart.substring(4, 6));
        int day = Integer.parseInt(datePart.substring(6, 8));
        int hour = Integer.parseInt(timePart.substring(0, 2));
        int minute = Integer.parseInt(timePart.substring(2, 4));

        return LocalDateTime.of(year, month, day, hour, minute, 0);
    }

    /**
     * 解析纯数字日期（YYYYMMDD）
     */
    private LocalDateTime parseNumericDateOnly(Matcher matcher) {
        String dateStr = matcher.group(1);

        int year = Integer.parseInt(dateStr.substring(0, 4));
        int month = Integer.parseInt(dateStr.substring(4, 6));
        int day = Integer.parseInt(dateStr.substring(6, 8));

        return LocalDateTime.of(year, month, day, 0, 0, 0);
    }

    // ==================== Token处理 ====================

    /**
     * 找出时间范围相关的所有token索引
     */
    private List<Integer> findTokenIndicesInRange(
            List<Token> tokens,
            String fullText,
            int rangeStartIndex,
            String dateRangeText) {

        // 计算时间范围表达式的结束位置
        int rangeEndIndex = calculateRangeEndIndex(fullText, rangeStartIndex);

        // 找出位置在范围内的所有token
        return findTokensInTextRange(tokens, rangeStartIndex, rangeEndIndex);
    }

    /**
     * 计算时间范围表达式的结束位置
     */
    private int calculateRangeEndIndex(String fullText, int startIndex) {
        String substring = fullText.substring(startIndex);

        // 提取所有日期
        List<String> dates = extractAllDates(substring);
        if (dates.isEmpty()) {
            return startIndex;
        }

        // 找到最后一个日期的结束位置
        String lastDate = dates.get(dates.size() - 1);
        int lastDateIndex = substring.lastIndexOf(lastDate);

        if (lastDateIndex == -1) {
            return startIndex;
        }

        return startIndex + lastDateIndex + lastDate.length();
    }

    /**
     * 找出文本位置在指定范围内的所有token
     */
    private List<Integer> findTokensInTextRange(
            List<Token> tokens,
            int rangeStart,
            int rangeEnd) {

        List<Integer> indices = new ArrayList<>();
        int currentPos = 0;

        for (int i = 0; i < tokens.size(); i++) {
            String tokenText = tokens.get(i).getText();
            int tokenStart = currentPos;

            // token的起始位置在范围内
            if (tokenStart >= rangeStart && tokenStart < rangeEnd) {
                indices.add(i);
            }

            currentPos += tokenText.length();
        }

        return indices;
    }

    /**
     * 从tokens列表中移除指定索引的tokens
     */
    private void removeTokensByIndices(List<Token> tokens, List<Integer> indicesToRemove) {
        if (indicesToRemove == null || indicesToRemove.isEmpty()) {
            return;
        }

        // 按降序排序，从后往前删除
        Collections.sort(indicesToRemove, Collections.reverseOrder());

        // 移除tokens
        for (Integer index : indicesToRemove) {
            if (index >= 0 && index < tokens.size()) {
                tokens.remove(index.intValue());
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 日期格式类型
     */
    private enum DateFormatType {
        ISO_WITH_SECONDS,
        ISO_WITH_MINUTES,
        STANDARD_WITH_SECONDS,
        STANDARD_WITH_MINUTES,
        COMPACT_WITH_SECONDS,
        COMPACT_WITH_MINUTES,
        SLASH_WITH_SECONDS,
        SLASH_WITH_MINUTES,
        SLASH_COMPACT_WITH_SECONDS,
        SLASH_COMPACT_WITH_MINUTES,
        CHINESE_WITH_SECONDS,
        CHINESE_WITH_MINUTES,
        CHINESE_COMPACT_WITH_SECONDS,
        CHINESE_COMPACT_WITH_MINUTES,
        NUMERIC_WITH_SECONDS,
        NUMERIC_WITH_MINUTES,
        DATE_ONLY_DASH,
        DATE_ONLY_SLASH,
        DATE_ONLY_CHINESE,
        DATE_ONLY_NUMERIC
    }

    /**
     * 日期格式模式
     */
    private static class DatePattern {
        final Pattern pattern;
        final DateFormatType type;

        DatePattern(String regex, DateFormatType type) {
            this.pattern = Pattern.compile(regex);
            this.type = type;
        }
    }

    /**
     * 日期匹配结果
     */
    private static class DateMatch {
        final String text;
        final int start;
        final int end;
        final DateFormatType type;

        DateMatch(String text, int start, int end, DateFormatType type) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.type = type;
        }
    }

    /**
     * 时间范围提取结果
     */
    private static class DateRangeExtractionResult {
        final DateRangeIntent dateRange;
        final List<Integer> tokensToRemove;

        DateRangeExtractionResult(DateRangeIntent dateRange, List<Integer> tokensToRemove) {
            this.dateRange = dateRange;
            this.tokensToRemove = tokensToRemove;
        }
    }
}
