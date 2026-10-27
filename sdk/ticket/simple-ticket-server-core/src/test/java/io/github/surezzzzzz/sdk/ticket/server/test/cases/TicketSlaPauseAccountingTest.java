package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SLA 暂停账测试（DESIGN §9 唯一口径：办结到期 = now > resolutionDeadline + 暂停累计；
 * PENDING_INFO 进行中该式恒暂缓——有效期限随 now 同速延伸）。
 *
 * @author surezzzzzz
 */
@Slf4j
class TicketSlaPauseAccountingTest {

    private static final Instant T0 = Instant.parse("2026-10-10T00:00:00Z");

    private Ticket claimed() {
        Ticket ticket = Ticket.newTicket("TK261010000001", "fault", "标题", "描述", "{}",
                TicketPriority.NORMAL, "user-1", "用户一", TicketSource.PORTAL, null, null, null,
                60, 480, T0);
        ticket.claim("grp", "agent-1", "处理人一", T0.plusSeconds(600));
        return ticket;
    }

    @Test
    void pauseSuspendsResolutionDeadline() {
        Ticket ticket = claimed();
        // T0+30min 暂停，暂停中恒不超时（即使基线已过）
        Instant pauseAt = T0.plusSeconds(1800);
        ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, pauseAt);
        for (long offsetMinutes : new long[]{10, 60, 600, 6000}) {
            Instant probe = pauseAt.plusSeconds(offsetMinutes * 60);
            assertFalse(ticket.isResolutionOverdue(probe), "暂停中第 " + offsetMinutes + " 分钟不应超时");
        }
    }

    @Test
    void supplySettlesPauseAccount() {
        Ticket ticket = claimed();
        Instant pauseAt = T0.plusSeconds(1800);
        ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, pauseAt);
        ticket.supply(pauseAt.plusSeconds(3600));
        log.debug("核对暂停结算：累计毫秒={}", ticket.getPendingAccumulatedMs());
        assertEquals(3_600_000L, ticket.getPendingAccumulatedMs());
        assertEquals(null, ticket.getPauseStartedAt());
        // 有效期限 = 基线 + 累计：T0+8h 基线，+1h 暂停 → T0+9h 起才超时
        assertFalse(ticket.isResolutionOverdue(T0.plusSeconds(8 * 3600 + 1799)));
        assertTrue(ticket.isResolutionOverdue(T0.plusSeconds(9 * 3600 + 1)));
    }

    @Test
    void resolveDuringPauseSettlesFirst() {
        Ticket ticket = claimed();
        Instant pauseAt = T0.plusSeconds(1800);
        ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, pauseAt);
        ticket.resolve(168, pauseAt.plusSeconds(1800));
        assertEquals(1_800_000L, ticket.getPendingAccumulatedMs(), "办结先结暂停账");
        assertEquals(null, ticket.getPauseStartedAt());
        assertEquals(pauseAt.plusSeconds(1800).plusSeconds(168 * 3600), ticket.getConfirmDeadline());
    }

    @Test
    void responseDeadlineNotPaused() {
        Ticket ticket = claimed();
        Instant pauseAt = T0.plusSeconds(1800);
        ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, pauseAt);
        // 已受理：响应不超时；未受理单才判响应超时
        assertFalse(ticket.isResponseOverdue(pauseAt.plusSeconds(99999)));
        Ticket fresh = Ticket.newTicket("TK261010000002", "fault", "标题", "描述", "{}",
                TicketPriority.NORMAL, "user-1", "用户一", TicketSource.PORTAL, null, null, null,
                60, 480, T0);
        assertTrue(fresh.isResponseOverdue(T0.plusSeconds(3601)), "未受理过响应限应超时");
        assertFalse(fresh.isResponseOverdue(T0.plusSeconds(3599)));
    }

    @Test
    void confirmTimeoutOnlyFromResolved() {
        Ticket ticket = claimed();
        ticket.resolve(168, T0.plusSeconds(1800));
        assertFalse(ticket.isConfirmOverdue(T0.plusSeconds(1800 + 168 * 3600 - 1)));
        assertTrue(ticket.isConfirmOverdue(T0.plusSeconds(1800 + 168 * 3600 + 1)));
        ticket.confirmTimeout(T0.plusSeconds(999999));
        assertFalse(ticket.isConfirmOverdue(T0.plusSeconds(99999999)), "关闭后不再判确认超时");
    }

    @Test
    void rejectRegeneratesConfirmDeadlineOnNextResolve() {
        Ticket ticket = claimed();
        Instant resolvedAt = T0.plusSeconds(1800);
        ticket.resolve(24, resolvedAt);
        ticket.reject(resolvedAt.plusSeconds(60));
        assertEquals(null, ticket.getConfirmDeadline());
        Instant secondResolve = resolvedAt.plusSeconds(3600);
        ticket.resolve(24, secondResolve);
        assertEquals(secondResolve.plusSeconds(24 * 3600), ticket.getConfirmDeadline(), "二次办结重起确认期限");
    }
}
