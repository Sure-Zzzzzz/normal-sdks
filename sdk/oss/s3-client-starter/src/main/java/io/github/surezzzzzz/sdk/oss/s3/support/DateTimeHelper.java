package io.github.surezzzzzz.sdk.oss.s3.support;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 日期时间转换工具类
 */
public final class DateTimeHelper {

    private DateTimeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将 Date 转换为 OffsetDateTime
     */
    public static OffsetDateTime toOffsetDateTime(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        return instant.atZone(zoneId).toOffsetDateTime();
    }
}