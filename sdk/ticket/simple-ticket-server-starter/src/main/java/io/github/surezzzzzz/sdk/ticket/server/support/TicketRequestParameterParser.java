package io.github.surezzzzzz.sdk.ticket.server.support;

import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单请求参数解析，非法输入统一转为字段级校验错误。
 *
 * @author surezzzzzz
 */
public final class TicketRequestParameterParser {

    private static final int DEFAULT_PAGE = 1;

    private static final int MAX_PAGE_SIZE = 100;

    private TicketRequestParameterParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 规范化页码。页码从 1 开始，非正数按第一页处理。
     *
     * @param page 请求页码
     * @return 可用于查询和响应回显的页码
     */
    public static int safePage(int page) {
        return page < DEFAULT_PAGE ? DEFAULT_PAGE : page;
    }

    /**
     * 规范化页大小。页大小至少为 1，最多为 100。
     *
     * @param size 请求页大小
     * @return 可用于查询和响应回显的页大小
     */
    public static int safeSize(int size) {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    /**
     * 解析状态过滤。
     *
     * @param names 请求中的状态名
     * @return 状态列表
     */
    public static List<TicketStatus> statuses(List<String> names) {
        List<TicketStatus> statuses = new ArrayList<>();
        if (names == null) {
            return statuses;
        }
        for (String name : names) {
            boolean matched = false;
            for (TicketStatus status : TicketStatus.values()) {
                if (status.name().equals(name)) {
                    statuses.add(status);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new TicketValidationException("status", "不合法");
            }
        }
        return statuses;
    }

    /**
     * 解析待补充原因。
     *
     * @param name 请求中的原因名
     * @return 待补充原因
     */
    public static RequestInfoReason reason(String name) {
        for (RequestInfoReason reason : RequestInfoReason.values()) {
            if (reason.name().equals(name)) {
                return reason;
            }
        }
        throw new TicketValidationException("reason", "不合法");
    }

    /**
     * 校验时间线游标；空值保持无游标语义。
     *
     * @param before ISO-8601 游标
     */
    public static void validateBefore(String before) {
        if (before == null || before.trim().isEmpty()) {
            return;
        }
        try {
            Instant.parse(before);
        } catch (DateTimeParseException e) {
            throw new TicketValidationException("before", "必须是 ISO-8601 时间");
        }
    }

    /**
     * 解析写请求的 If-Match 版本。允许 3、"3" 和 W/"3"；缺失时保留
     * 兼容的无门槛服务层调用语义。
     *
     * @param value If-Match 请求头
     * @return 期望版本，缺失时为 null
     */
    public static Long expectedVersion(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            long version = Long.parseLong(normalized);
            if (version < 0) {
                throw new NumberFormatException("negative");
            }
            return version;
        } catch (NumberFormatException e) {
            throw new TicketValidationException("If-Match", "必须是非负整数版本");
        }
    }

    /**
     * 解析优先级过滤。
     *
     * @param value 优先级名称
     * @return 优先级，空值为 null
     */
    public static TicketPriority priority(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (TicketPriority priority : TicketPriority.values()) {
            if (priority.name().equals(normalized)) {
                return priority;
            }
        }
        throw new TicketValidationException("priority", "不合法");
    }

    /**
     * 解析北京时间日期边界；日期为闭区间，返回 UTC Instant 的起止值。
     *
     * @param from 起始日期
     * @param to 结束日期
     * @return 两个边界，均为空表示不限制
     */
    public static Instant[] dateRange(String from, String to) {
        if ((from == null || from.trim().isEmpty()) && (to == null || to.trim().isEmpty())) {
            return new Instant[] {null, null};
        }
        try {
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            LocalDate start = from == null || from.trim().isEmpty() ? null : LocalDate.parse(from.trim());
            LocalDate end = to == null || to.trim().isEmpty() ? null : LocalDate.parse(to.trim());
            if (start != null && end != null && end.isBefore(start)) {
                throw new TicketValidationException("from/to", "起始日期不能晚于结束日期");
            }
            Instant startAt = start == null ? null : start.atStartOfDay(zone).toInstant();
            Instant endAt = end == null ? null : end.plusDays(1).atStartOfDay(zone).toInstant();
            return new Instant[] {startAt, endAt};
        } catch (java.time.format.DateTimeParseException e) {
            throw new TicketValidationException("from/to", "必须为 yyyy-MM-dd");
        }
    }
}
