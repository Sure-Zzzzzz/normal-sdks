package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketNoHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * core 契约形状测试：错误码-消息一一对应、单号格式、常量键面、优先级排序、组判定。
 *
 * @author surezzzzzz
 */
@Slf4j
class TicketCoreShapeTest {

    @Test
    void errorCodeAndMessagePairedExactly() {
        List<String> codes = publicStringConstants(ErrorCode.class);
        List<String> messages = publicStringConstants(ErrorMessage.class);
        log.debug("核对错误码与消息常量数量：codeCount={} messageCount={}", codes.size(), messages.size());
        assertEquals(codes.size(), messages.size(), "错误码与消息数量一致");
        for (String code : codes) {
            assertEquals(code, code, "错误码自查");
        }
        assertEquals(codes, publicStringConstants(ErrorCode.class));
        // 同名一一对应：ErrorMessage 每个常量名都能在 ErrorCode 找到同值常量
        for (String name : constantNames(ErrorMessage.class)) {
            assertTrue(constantNames(ErrorCode.class).contains(name), "消息缺错误码对偶：" + name);
        }
        assertEquals(14, codes.size(), "DESIGN §6.6 错误码家族数（服务失败码用于事务回滚后的统一错误映射）");
    }

    @Test
    void ticketNoFormatAndValidation() {
        assertEquals("TK261010000001",
                TicketNoHelper.format(LocalDateTime.of(2026, 10, 10, 9, 0), 1));
        assertEquals("TK261011123456",
                TicketNoHelper.format(LocalDateTime.of(2026, 10, 11, 23, 59), 123456));
        assertTrue(TicketNoHelper.isValid("TK261010000001"));
        assertTrue(TicketNoHelper.isValid("TK991231999999"));
        assertFalse(TicketNoHelper.isValid(null));
        assertFalse(TicketNoHelper.isValid("TK261010000001X"));
        assertFalse(TicketNoHelper.isValid("WK261010000001"));
        assertFalse(TicketNoHelper.isValid("TK26101"));
        assertEquals("261010", TicketNoHelper.daySegmentOf("TK261010000001"));
        assertEquals("Asia/Shanghai", TicketNoHelper.beijingZone().getId());
    }

    @Test
    void configPrefixAndDefaults() {
        assertEquals("io.github.surezzzzzz.sdk.ticket.server", SimpleTicketServerConstant.CONFIG_PREFIX);
        assertEquals("/api/ticket", SimpleTicketServerConstant.DEFAULT_API_BASE_PATH);
        assertEquals("TK", SimpleTicketServerConstant.TICKET_NO_PREFIX);
        assertEquals(6, SimpleTicketServerConstant.TICKET_NO_SEQUENCE_WIDTH);
        assertEquals(1, SimpleTicketServerConstant.STAR_MIN);
        assertEquals(5, SimpleTicketServerConstant.STAR_MAX);
        assertEquals(168L, SimpleTicketServerConstant.DEFAULT_CONFIRM_TIMEOUT_HOURS);
    }

    @Test
    void groupMembershipAndLeaderJudgement() {
        io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup group =
                io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup.builder()
                        .code("grp").name("运维组")
                        .memberSubjectIds(java.util.Arrays.asList("s1", "s2"))
                        .leaderSubjectId("s1").enabled(true).build();
        assertTrue(group.hasMember("s1"));
        assertTrue(group.hasMember("s2"));
        assertFalse(group.hasMember("s3"));
        assertFalse(group.hasMember(null));
        assertTrue(group.isLeader("s1"));
        assertFalse(group.isLeader("s2"));
    }

    @Test
    void priorityOrderForQueueSort() {
        assertTrue(TicketPriority.URGENT.getSortOrder() > TicketPriority.HIGH.getSortOrder());
        assertTrue(TicketPriority.HIGH.getSortOrder() > TicketPriority.NORMAL.getSortOrder());
        assertTrue(TicketPriority.NORMAL.getSortOrder() > TicketPriority.LOW.getSortOrder());
        assertEquals(4, TicketPriority.values().length);
    }

    // ==================== 辅助 ====================

    private List<String> publicStringConstants(Class<?> type) {
        List<String> values = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                values.add(assertDoesNotThrow(() -> (String) field.get(null)));
            }
        }
        return values;
    }

    private List<String> constantNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                names.add(field.getName());
            }
        }
        return names;
    }
}
