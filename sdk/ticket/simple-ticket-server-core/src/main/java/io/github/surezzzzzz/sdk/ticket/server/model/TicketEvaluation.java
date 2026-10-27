package io.github.surezzzzzz.sdk.ticket.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 工单评价（ticket_evaluation 表）。CLOSED 后一次不可改；confirm 关闭可评、超时关闭不可评（DESIGN §6.5）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEvaluation {

    private Long id;

    /**
     * 工单 ID（唯一）
     */
    private Long ticketId;

    /**
     * 评价人（提单人）
     */
    private String evaluatorSubjectId;

    /**
     * 星级 1-5
     */
    private Integer star;

    /**
     * 评语
     */
    private String comment;

    /**
     * 评价时刻
     */
    private Instant evaluatedAt;
}
