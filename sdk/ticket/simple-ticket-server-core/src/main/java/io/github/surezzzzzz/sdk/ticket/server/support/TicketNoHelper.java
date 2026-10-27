package io.github.surezzzzzz.sdk.ticket.server.support;

import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 单号格式工具（TK+北京日期+6 位日序列；生成侧在持久层，本类只管格式与解析）。
 *
 * @author surezzzzzz
 */
public final class TicketNoHelper {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private TicketNoHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 格式化单号。
     *
     * @param day       北京日期
     * @param sequence  日序列（1 起增）
     * @return 单号
     */
    public static String format(LocalDateTime day, long sequence) {
        String date = day.format(DAY_FORMAT);
        return SimpleTicketServerConstant.TICKET_NO_PREFIX + date
                + String.format("%0" + SimpleTicketServerConstant.TICKET_NO_SEQUENCE_WIDTH + "d", sequence);
    }

    /**
     * 校验单号形态（TK+6 位日期+6 位序列）。
     *
     * @param ticketNo 单号
     * @return 合法与否
     */
    public static boolean isValid(String ticketNo) {
        if (ticketNo == null || ticketNo.length() != 2 + 6 + SimpleTicketServerConstant.TICKET_NO_SEQUENCE_WIDTH) {
            return false;
        }
        if (!ticketNo.startsWith(SimpleTicketServerConstant.TICKET_NO_PREFIX)) {
            return false;
        }
        for (int i = 2; i < ticketNo.length(); i++) {
            char c = ticketNo.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 取单号的北京日期段（yyMMdd）。
     *
     * @param ticketNo 单号
     * @return 日期串
     */
    public static String daySegmentOf(String ticketNo) {
        return ticketNo.substring(2, 8);
    }

    /**
     * 北京时区（单号日期与展示墙钟统一锚）。
     *
     * @return 时区
     */
    public static ZoneId beijingZone() {
        return ZoneId.of("Asia/Shanghai");
    }
}
