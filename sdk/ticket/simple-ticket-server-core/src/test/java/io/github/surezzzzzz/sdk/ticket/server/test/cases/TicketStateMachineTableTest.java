package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketAction;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketStateException;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.statemachine.TicketStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机全表测试：Ticket 富方法与 TicketStateMachine 转移表双向对齐；动作×状态全格唯一解。
 *
 * @author surezzzzzz
 */
@Slf4j
class TicketStateMachineTableTest {

    private static final Instant NOW = Instant.parse("2026-10-10T02:00:00Z");

    private Ticket ticket(TicketStatus status) {
        Ticket ticket = Ticket.newTicket("TK261010000001", "fault", "标题", "描述", "{}",
                TicketPriority.NORMAL, "user-1", "用户一", TicketSource.PORTAL, null, null, null,
                60, 480, NOW);
        switch (status) {
            case PROCESSING:
                ticket.claim("grp", "agent-1", "处理人一", NOW.plusSeconds(60));
                return ticket;
            case PENDING_INFO:
                ticket.claim("grp", "agent-1", "处理人一", NOW.plusSeconds(60));
                ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, NOW.plusSeconds(120));
                return ticket;
            case RESOLVED:
                ticket.claim("grp", "agent-1", "处理人一", NOW.plusSeconds(60));
                ticket.resolve(168, NOW.plusSeconds(180));
                return ticket;
            case CLOSED:
                ticket.claim("grp", "agent-1", "处理人一", NOW.plusSeconds(60));
                ticket.resolve(168, NOW.plusSeconds(180));
                ticket.confirm(NOW.plusSeconds(240));
                return ticket;
            case WITHDRAWN:
                ticket.withdraw(NOW.plusSeconds(60));
                return ticket;
            default:
                return ticket;
        }
    }

    @Test
    void richModelMatchesTransitionTable() {
        log.debug("核对状态机矩阵：actionCount={} statusCount={}",
                TicketAction.values().length, TicketStatus.values().length);
        // 转移表允许的格子，富方法必须成功且落位目标状态（TRANSFER 除外，状态保持）
        for (TicketAction action : TicketAction.values()) {
            for (TicketStatus status : TicketStatus.values()) {
                boolean allowed = TicketStateMachine.canFire(action, status);
                boolean succeeded = tryFire(action, status);
                assertEquals(allowed, succeeded,
                        action + " from " + status + " 表与富方法不一致");
                boolean keepsStatus = action == TicketAction.TRANSFER
                        || (action == TicketAction.ASSIGN && status != TicketStatus.SUBMITTED);
                if (allowed && !keepsStatus) {
                    TicketStatus target = TicketStateMachine.targetOf(action);
                    if (target != null) {
                        Ticket fired = ticket(status);
                        fireOn(fired, action);
                        assertEquals(target, fired.getStatus(), action + " 目标状态与表不一致");
                    }
                }
            }
        }
    }

    @Test
    void terminalStatesAdmitNoAction() {
        for (TicketAction action : TicketAction.values()) {
            assertFalse(TicketStateMachine.canFire(action, TicketStatus.CLOSED), "CLOSED 应拒绝 " + action);
            assertFalse(TicketStateMachine.canFire(action, TicketStatus.WITHDRAWN), "WITHDRAWN 应拒绝 " + action);
        }
    }

    @Test
    void everyActionHasAllowedFromSet() {
        EnumSet<TicketAction> covered = EnumSet.noneOf(TicketAction.class);
        for (TicketAction action : TicketAction.values()) {
            Set<TicketStatus> allowed = TicketStateMachine.allowedFrom(action);
            assertTrue(allowed.isEmpty() == false, action + " 无前置状态集");
            covered.add(action);
        }
        assertEquals(EnumSet.allOf(TicketAction.class), covered);
    }

    @Test
    void claimSetsAssigneeAndAcceptedAt() {
        Ticket ticket = ticket(TicketStatus.SUBMITTED);
        ticket.claim("grp-2", "agent-9", "处理人九", NOW.plusSeconds(30));
        assertEquals(TicketStatus.PROCESSING, ticket.getStatus());
        assertEquals("grp-2", ticket.getAssigneeGroup());
        assertEquals("agent-9", ticket.getAssigneeSubjectId());
        assertEquals(NOW.plusSeconds(30), ticket.getAcceptedAt());
        assertEquals(1L, ticket.getVersion());
    }

    @Test
    void assignFromSubmittedEntersProcessing() {
        Ticket ticket = ticket(TicketStatus.SUBMITTED);
        ticket.assign("grp", "agent-2", "处理人二", NOW.plusSeconds(10));
        assertEquals(TicketStatus.PROCESSING, ticket.getStatus());
        assertEquals(NOW.plusSeconds(10), ticket.getAcceptedAt());
    }

    @Test
    void assignKeepsPauseAccount() {
        Ticket ticket = ticket(TicketStatus.PENDING_INFO);
        Instant pauseAt = NOW.plusSeconds(120);
        ticket.assign("grp-3", "agent-3", "处理人三", NOW.plusSeconds(200));
        assertEquals(TicketStatus.PENDING_INFO, ticket.getStatus());
        assertEquals(pauseAt, ticket.getPauseStartedAt(), "分派不清暂停账");
    }

    @Test
    void rejectKeepsAssigneeAndClearsConfirmDeadline() {
        Ticket ticket = ticket(TicketStatus.RESOLVED);
        ticket.reject(NOW.plusSeconds(300));
        assertEquals(TicketStatus.PROCESSING, ticket.getStatus());
        assertEquals("agent-1", ticket.getAssigneeSubjectId(), "重开保留处理人");
        assertEquals(null, ticket.getConfirmDeadline());
        assertEquals(null, ticket.getResolvedAt());
    }

    @Test
    void withdrawDuringPauseSettlesAccount() {
        Ticket ticket = ticket(TicketStatus.PENDING_INFO);
        ticket.withdraw(NOW.plusSeconds(300));
        assertEquals(TicketStatus.WITHDRAWN, ticket.getStatus());
        assertEquals(null, ticket.getPauseStartedAt());
        assertEquals(180_000L, ticket.getPendingAccumulatedMs());
    }

    @Test
    void illegalTransitionThrows409Family() {
        assertThrows(TicketStateException.class, () -> ticket(TicketStatus.SUBMITTED).resolve(168, NOW));
        assertThrows(TicketStateException.class, () -> ticket(TicketStatus.CLOSED).claim("g", "a", "n", NOW));
        assertThrows(TicketStateException.class,
                () -> ticket(TicketStatus.PROCESSING).supply(NOW));
        assertThrows(TicketStateException.class,
                () -> ticket(TicketStatus.PENDING_INFO).requestInfo(RequestInfoReason.AWAIT_MATERIAL, NOW));
    }

    @Test
    void newTicketDefaultsAndDeadlines() {
        Ticket ticket = ticket(TicketStatus.SUBMITTED);
        assertEquals(TicketPriority.NORMAL, ticket.getPriority());
        assertEquals(TicketStatus.SUBMITTED, ticket.getStatus());
        assertEquals(NOW.plusSeconds(3600), ticket.getResponseDeadline());
        assertEquals(NOW.plusSeconds(28_800), ticket.getResolutionDeadline());
        assertThrows(io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException.class,
                () -> Ticket.newTicket("TK261010000002", "fault", "  ", "d", "{}", null,
                        "u", "n", TicketSource.PORTAL, null, null, null, 1, 1, NOW));
    }

    // ==================== 辅助：动作分发 ====================

    private boolean tryFire(TicketAction action, TicketStatus status) {
        try {
            fireOn(ticket(status), action);
            return true;
        } catch (TicketStateException e) {
            return false;
        }
    }

    private void fireOn(Ticket ticket, TicketAction action) {
        switch (action) {
            case CLAIM:
                ticket.claim("grp", "agent-x", "处理人", NOW);
                break;
            case ASSIGN:
                ticket.assign("grp", "agent-x", "处理人", NOW);
                break;
            case TRANSFER:
                ticket.transfer("grp", "agent-x", "处理人", NOW);
                break;
            case REQUEST_INFO:
                ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, NOW);
                break;
            case SUPPLY:
                ticket.supply(NOW);
                break;
            case RESOLVE:
                ticket.resolve(168, NOW);
                break;
            case CONFIRM:
                ticket.confirm(NOW);
                break;
            case REJECT:
                ticket.reject(NOW);
                break;
            case WITHDRAW:
                ticket.withdraw(NOW);
                break;
            case CONFIRM_TIMEOUT:
                ticket.confirmTimeout(NOW);
                break;
            default:
                throw new AssertionError(action.name());
        }
    }
}
