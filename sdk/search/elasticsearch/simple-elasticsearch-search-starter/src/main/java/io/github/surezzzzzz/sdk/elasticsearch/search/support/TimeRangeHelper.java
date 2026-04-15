package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间范围解析工具
 *
 * @author surezzzzzz
 */
public final class TimeRangeHelper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private TimeRangeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析时间字符串为毫秒，用于数值比较（如 PIT keepAlive 上限校验）
     * 支持 d/h/m/s 单位，例如："5m" → 300000，"1h" → 3600000
     *
     * @param timeRange 时间字符串
     * @return 毫秒数
     * @throws QueryException 格式不合法时
     */
    public static long parseToMillis(String timeRange) {
        long value = parseValue(timeRange);
        String s = timeRange.trim().toLowerCase();
        if (s.endsWith("d")) return value * 86400_000L;
        if (s.endsWith("h")) return value * 3600_000L;
        if (s.endsWith("m")) return value * 60_000L;
        return value * 1_000L;
    }

    /**
     * 根据时间字符串构建"最近 N 时间"的 DateRange
     * from = 当前时间 - N，to = 当前时间
     * 支持 d/h/m/s 单位，例如："30d" → 最近 30 天
     *
     * @param timeRange 时间字符串
     * @return DateRange
     * @throws QueryException 格式不合法时
     */
    public static QueryRequest.DateRange buildRecentRange(String timeRange) {
        long value = parseValue(timeRange);
        String s = timeRange.trim().toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from;
        if (s.endsWith("d"))      from = now.minusDays(value);
        else if (s.endsWith("h")) from = now.minusHours(value);
        else if (s.endsWith("m")) from = now.minusMinutes(value);
        else                      from = now.minusSeconds(value);

        return QueryRequest.DateRange.builder()
                .from(from.format(FORMATTER))
                .to(now.format(FORMATTER))
                .build();
    }

    /**
     * 解析数值部分，同时校验格式
     */
    private static long parseValue(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            throw new QueryException(ErrorCode.TIME_RANGE_INVALID_FORMAT,
                    String.format(ErrorMessage.TIME_RANGE_INVALID_FORMAT, timeRange));
        }
        String s = timeRange.trim().toLowerCase();
        if (s.endsWith("d") || s.endsWith("h") || s.endsWith("m") || s.endsWith("s")) {
            try {
                return Long.parseLong(s.substring(0, s.length() - 1));
            } catch (NumberFormatException ignored) {
            }
        }
        throw new QueryException(ErrorCode.TIME_RANGE_INVALID_FORMAT,
                String.format(ErrorMessage.TIME_RANGE_INVALID_FORMAT, timeRange));
    }
}
