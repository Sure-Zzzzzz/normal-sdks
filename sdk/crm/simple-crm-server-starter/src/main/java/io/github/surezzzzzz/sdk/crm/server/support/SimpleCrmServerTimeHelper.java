package io.github.surezzzzzz.sdk.crm.server.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * CRM 持久化时间边界助手。
 *
 * <p>CRM 的时间列使用 {@code TIMESTAMP(3)}，因此进入领域事实的系统时间统一保留到毫秒，
 * 让首次响应、数据库回读和幂等重放使用同一份时间值。</p>
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerTimeHelper {

    private SimpleCrmServerTimeHelper() {
    }

    /**
     * 将时间规整到数据库支持的毫秒精度。
     *
     * @param instant 原始时间
     * @return 毫秒精度时间
     */
    public static Instant truncateToMillis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }
}
