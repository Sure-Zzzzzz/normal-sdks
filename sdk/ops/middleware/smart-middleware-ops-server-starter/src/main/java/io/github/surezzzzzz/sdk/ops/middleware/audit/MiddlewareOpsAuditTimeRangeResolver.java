package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Middleware Ops 审计时间范围解析器。
 *
 * @author surezzzzzz
 */
public final class MiddlewareOpsAuditTimeRangeResolver {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(
            SmartMiddlewareOpsServerConstant.AUDIT_TIME_PATTERN);

    private MiddlewareOpsAuditTimeRangeResolver() {
        throw new UnsupportedOperationException("审计时间范围解析器不能实例化");
    }

    /**
     * 解析审计时间筛选参数。
     *
     * @param range           快捷时间范围
     * @param from            自定义开始时间
     * @param to              自定义结束时间
     * @param auditProperties 审计配置
     * @return 有效时间范围；未传入筛选时返回 null
     */
    public static MiddlewareOpsAuditTimeRange resolve(String range, String from, String to,
                                                      SmartMiddlewareOpsServerProperties.Audit auditProperties) {
        if (hasText(range)) {
            if (hasText(from) || hasText(to)) {
                throw invalid();
            }
            return shortcut(range, auditProperties.getMaxRangeDays());
        }
        if (!hasText(from) && !hasText(to)) {
            return null;
        }
        if (!hasText(from) || !hasText(to)) {
            throw invalid();
        }
        LocalDateTime start = parse(from);
        LocalDateTime end = parse(to);
        if (!start.isBefore(end) || exceeds(start, end, auditProperties.getMaxRangeDays())) {
            throw invalid();
        }
        return MiddlewareOpsAuditTimeRange.builder().from(format(start)).to(format(end)).build();
    }

    private static MiddlewareOpsAuditTimeRange shortcut(String range, Integer maxRangeDays) {
        int days = shortcutDays(range);
        if (maxRangeDays == null || maxRangeDays <= 0 || days > maxRangeDays) {
            throw invalid();
        }
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        return MiddlewareOpsAuditTimeRange.builder().from(format(end.minusDays(days))).to(format(end)).build();
    }

    private static int shortcutDays(String range) {
        if (SmartMiddlewareOpsServerConstant.AUDIT_RANGE_1_DAY.equals(range)) {
            return SmartMiddlewareOpsServerConstant.AUDIT_RANGE_1_DAY_DAYS;
        }
        if (SmartMiddlewareOpsServerConstant.AUDIT_RANGE_7_DAYS.equals(range)) {
            return SmartMiddlewareOpsServerConstant.AUDIT_RANGE_7_DAYS_DAYS;
        }
        if (SmartMiddlewareOpsServerConstant.AUDIT_RANGE_30_DAYS.equals(range)) {
            return SmartMiddlewareOpsServerConstant.AUDIT_RANGE_30_DAYS_DAYS;
        }
        if (SmartMiddlewareOpsServerConstant.AUDIT_RANGE_90_DAYS.equals(range)) {
            return SmartMiddlewareOpsServerConstant.AUDIT_RANGE_90_DAYS_DAYS;
        }
        throw invalid();
    }

    private static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    private static boolean exceeds(LocalDateTime from, LocalDateTime to, Integer maxRangeDays) {
        return maxRangeDays == null || maxRangeDays <= 0
                || Duration.between(from, to).compareTo(Duration.ofDays(maxRangeDays)) > 0;
    }

    private static String format(LocalDateTime value) {
        return FORMATTER.format(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static MiddlewareOpsException invalid() {
        return new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "审计时间范围无效");
    }
}
