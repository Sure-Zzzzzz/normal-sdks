package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.DateRangeIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

import io.github.surezzzzzz.sdk.naturallanguage.parser.support.TokenHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间范围解析插件
 *
 * @author surezzzzzz
 */
@Slf4j
@NaturalLanguageParserComponent
@Order(10)
public class DateRangeParser implements NLParserPlugin {

    private static final Set<String> DATE_RANGE_KEYWORDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "时间范围", "日期范围",
                    "dateRange", "date range", "time range", "timeRange",
                    "date_range", "time_range"
            ))
    );

    private static final Set<String> RANGE_SEPARATORS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("到", "至", "~", "-"))
    );

    private static final List<DatePattern> DATE_PATTERNS = initDatePatterns();

    @Override
    public boolean supports(IntentType intentType) {
        return intentType == IntentType.QUERY;
    }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry keywordRegistry,
                      NLParserProperties properties, ParseResult result) {
        DateRangeIntent dateRange = doParse(tokens);
        if (dateRange != null) {
            result.setDateRange(dateRange);
        }
    }

    private DateRangeIntent doParse(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        String fullText = joinTokens(tokens);
        if (fullText.isEmpty()) {
            return null;
        }

        DateRangeExtractionResult extractionResult = extractDateRange(fullText, tokens);
        if (extractionResult == null || extractionResult.dateRange == null) {
            return null;
        }

        removeTokensByIndices(tokens, extractionResult.tokensToRemove);

        return extractionResult.dateRange;
    }

    private String joinTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            sb.append(token.getText());
        }
        return sb.toString();
    }

    private DateRangeExtractionResult extractDateRange(String fullText, List<Token> tokens) {
        String textLower = fullText.toLowerCase();

        for (String keyword : DATE_RANGE_KEYWORDS) {
            String keywordLower = keyword.toLowerCase();
            int keywordIndex = textLower.indexOf(keywordLower);

            if (keywordIndex == -1) {
                continue;
            }

            int afterIndex = keywordIndex + keyword.length();
            String afterKeyword = fullText.substring(afterIndex);

            DateRangeIntent range = findDateRangeInText(afterKeyword);
            if (range != null) {
                List<Integer> tokensToRemove = findTokenIndicesInRange(
                        tokens, fullText, keywordIndex, afterKeyword
                );
                return new DateRangeExtractionResult(range, tokensToRemove);
            }
        }

        return null;
    }

    private DateRangeIntent findDateRangeInText(String text) {
        List<String> dates = extractAllDates(text);
        if (dates.size() < 2) {
            return null;
        }
        if (!containsRangeSeparator(text)) {
            return null;
        }
        return buildDateRange(dates.get(0), dates.get(1));
    }

    private boolean containsRangeSeparator(String text) {
        for (String separator : RANGE_SEPARATORS) {
            if (text.contains(separator)) {
                return true;
            }
        }
        return false;
    }

    private DateRangeIntent buildDateRange(String fromDate, String toDate) {
        String fromISO = convertToISO(fromDate);
        String toISO = convertToISO(toDate);
        if (fromISO == null || toISO == null) {
            return null;
        }
        return DateRangeIntent.builder().from(fromISO).to(toISO).build();
    }

    private List<String> extractAllDates(String text) {
        List<DateMatch> matches = collectDateMatches(text);
        return deduplicateDateMatches(matches);
    }

    private List<DateMatch> collectDateMatches(String text) {
        List<DateMatch> matches = new ArrayList<>();
        for (DatePattern datePattern : DATE_PATTERNS) {
            Matcher matcher = datePattern.pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new DateMatch(matcher.group(), matcher.start(), matcher.end(), datePattern.type));
            }
        }
        return matches;
    }

    private List<String> deduplicateDateMatches(List<DateMatch> matches) {
        Collections.sort(matches, new Comparator<DateMatch>() {
            @Override
            public int compare(DateMatch a, DateMatch b) {
                if (a.start != b.start) {
                    return Integer.compare(a.start, b.start);
                }
                return Integer.compare(b.text.length(), a.text.length());
            }
        });

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

    private String convertToISO(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
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

    private LocalDateTime parseDateTime(Matcher matcher, DateFormatType formatType) {
        try {
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

    private LocalDateTime parseWithSeconds(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        int second = Integer.parseInt(matcher.group(6));
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    private LocalDateTime parseWithMinutes(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        return LocalDateTime.of(year, month, day, hour, minute, 0);
    }

    private LocalDateTime parseDateOnly(Matcher matcher) {
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        return LocalDateTime.of(year, month, day, 0, 0, 0);
    }

    private LocalDateTime parseNumericWithSeconds(Matcher matcher) {
        String datePart = matcher.group(1);
        String timePart = matcher.group(2);
        int year = Integer.parseInt(datePart.substring(0, 4));
        int month = Integer.parseInt(datePart.substring(4, 6));
        int day = Integer.parseInt(datePart.substring(6, 8));
        int hour = Integer.parseInt(timePart.substring(0, 2));
        int minute = Integer.parseInt(timePart.substring(2, 4));
        int second = Integer.parseInt(timePart.substring(4, 6));
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    private LocalDateTime parseNumericWithMinutes(Matcher matcher) {
        String datePart = matcher.group(1);
        String timePart = matcher.group(2);
        int year = Integer.parseInt(datePart.substring(0, 4));
        int month = Integer.parseInt(datePart.substring(4, 6));
        int day = Integer.parseInt(datePart.substring(6, 8));
        int hour = Integer.parseInt(timePart.substring(0, 2));
        int minute = Integer.parseInt(timePart.substring(2, 4));
        return LocalDateTime.of(year, month, day, hour, minute, 0);
    }

    private LocalDateTime parseNumericDateOnly(Matcher matcher) {
        String dateStr = matcher.group(1);
        int year = Integer.parseInt(dateStr.substring(0, 4));
        int month = Integer.parseInt(dateStr.substring(4, 6));
        int day = Integer.parseInt(dateStr.substring(6, 8));
        return LocalDateTime.of(year, month, day, 0, 0, 0);
    }

    private List<Integer> findTokenIndicesInRange(List<Token> tokens, String fullText,
                                                   int rangeStartIndex, String dateRangeText) {
        int rangeEndIndex = calculateRangeEndIndex(fullText, rangeStartIndex);
        return findTokensInTextRange(tokens, rangeStartIndex, rangeEndIndex);
    }

    private int calculateRangeEndIndex(String fullText, int startIndex) {
        String substring = fullText.substring(startIndex);
        List<String> dates = extractAllDates(substring);
        if (dates.isEmpty()) {
            return startIndex;
        }
        String lastDate = dates.get(dates.size() - 1);
        int lastDateIndex = substring.lastIndexOf(lastDate);
        if (lastDateIndex == -1) {
            return startIndex;
        }
        return startIndex + lastDateIndex + lastDate.length();
    }

    private List<Integer> findTokensInTextRange(List<Token> tokens, int rangeStart, int rangeEnd) {
        List<Integer> indices = new ArrayList<>();
        int currentPos = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String tokenText = tokens.get(i).getText();
            int tokenStart = currentPos;
            if (tokenStart >= rangeStart && tokenStart < rangeEnd) {
                indices.add(i);
            }
            currentPos += tokenText.length();
        }
        return indices;
    }

    private void removeTokensByIndices(List<Token> tokens, List<Integer> indicesToRemove) {
        TokenHelper.removeByIndices(tokens, indicesToRemove);
    }

    // ==================== Date pattern initialization ====================

    private static List<DatePattern> initDatePatterns() {
        List<DatePattern> patterns = new ArrayList<>();
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})T(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", DateFormatType.ISO_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})T(\\d{1,2}):(\\d{1,2})", DateFormatType.ISO_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", DateFormatType.STANDARD_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})", DateFormatType.STANDARD_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", DateFormatType.COMPACT_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})(\\d{1,2}):(\\d{1,2})", DateFormatType.COMPACT_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", DateFormatType.SLASH_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})", DateFormatType.SLASH_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})/(\\d{1,2})/(\\d{1,2})(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", DateFormatType.SLASH_COMPACT_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})/(\\d{1,2})/(\\d{1,2})(\\d{1,2}):(\\d{1,2})", DateFormatType.SLASH_COMPACT_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})时(\\d{1,2})分(\\d{1,2})秒", DateFormatType.CHINESE_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})时(\\d{1,2})分", DateFormatType.CHINESE_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})年(\\d{1,2})月(\\d{1,2})日(\\d{1,2})时(\\d{1,2})分(\\d{1,2})秒", DateFormatType.CHINESE_COMPACT_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{4})年(\\d{1,2})月(\\d{1,2})日(\\d{1,2})时(\\d{1,2})分", DateFormatType.CHINESE_COMPACT_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{8})\\s+(\\d{6})", DateFormatType.NUMERIC_WITH_SECONDS));
        patterns.add(new DatePattern("(\\d{8})\\s+(\\d{4})", DateFormatType.NUMERIC_WITH_MINUTES));
        patterns.add(new DatePattern("(\\d{4})-(\\d{1,2})-(\\d{1,2})", DateFormatType.DATE_ONLY_DASH));
        patterns.add(new DatePattern("(\\d{4})/(\\d{1,2})/(\\d{1,2})", DateFormatType.DATE_ONLY_SLASH));
        patterns.add(new DatePattern("(\\d{4})年(\\d{1,2})月(\\d{1,2})日", DateFormatType.DATE_ONLY_CHINESE));
        patterns.add(new DatePattern("(\\d{8})", DateFormatType.DATE_ONLY_NUMERIC));
        return Collections.unmodifiableList(patterns);
    }

    private enum DateFormatType {
        ISO_WITH_SECONDS, ISO_WITH_MINUTES,
        STANDARD_WITH_SECONDS, STANDARD_WITH_MINUTES,
        COMPACT_WITH_SECONDS, COMPACT_WITH_MINUTES,
        SLASH_WITH_SECONDS, SLASH_WITH_MINUTES,
        SLASH_COMPACT_WITH_SECONDS, SLASH_COMPACT_WITH_MINUTES,
        CHINESE_WITH_SECONDS, CHINESE_WITH_MINUTES,
        CHINESE_COMPACT_WITH_SECONDS, CHINESE_COMPACT_WITH_MINUTES,
        NUMERIC_WITH_SECONDS, NUMERIC_WITH_MINUTES,
        DATE_ONLY_DASH, DATE_ONLY_SLASH, DATE_ONLY_CHINESE, DATE_ONLY_NUMERIC
    }

    private static class DatePattern {
        final Pattern pattern;
        final DateFormatType type;
        DatePattern(String regex, DateFormatType type) {
            this.pattern = Pattern.compile(regex);
            this.type = type;
        }
    }

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

    private static class DateRangeExtractionResult {
        final DateRangeIntent dateRange;
        final List<Integer> tokensToRemove;
        DateRangeExtractionResult(DateRangeIntent dateRange, List<Integer> tokensToRemove) {
            this.dateRange = dateRange;
            this.tokensToRemove = tokensToRemove;
        }
    }
}
