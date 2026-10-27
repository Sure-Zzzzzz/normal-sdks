package io.github.surezzzzzz.sdk.ticket.server.model;

import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 状态转移记录（ticket_transition 表）。不可变只增，与留言分表（语义不同，DESIGN 二审 R36）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTransition {

    private Long id;

    /**
     * 所属工单 ID
     */
    private Long ticketId;

    /**
     * 起始状态
     */
    private TicketStatus fromStatus;

    /**
     * 目标状态
     */
    private TicketStatus toStatus;

    /**
     * 动作标识（与 Ticket 领域方法一一对应）
     */
    private String action;

    /**
     * 动作者标识（系统动作为 null）
     */
    private String actorSubjectId;

    /**
     * 动作者角色
     */
    private ActorRole actorRole;

    /**
     * 待补充原因（仅 REQUEST_INFO 携带）
     */
    private RequestInfoReason reasonCode;

    /**
     * 转移时刻
     */
    private Instant createdAt;
}
