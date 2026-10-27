package io.github.surezzzzzz.sdk.ticket.server.support;

import io.github.surezzzzzz.sdk.ticket.server.dto.CommentResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketDetailResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketGroupResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketSummaryResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketTypeResponse;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketComment;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketType;

import java.time.Instant;

/**
 * 领域→wire 响应映射（时间统一 ISO-8601 字符串；null 时刻保持 null）。
 *
 * @author surezzzzzz
 */
public final class TicketResponseMapper {

    private TicketResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 摘要映射。
     *
     * @param ticket 工单
     * @return 摘要响应
     */
    public static TicketSummaryResponse summaryOf(Ticket ticket) {
        return TicketSummaryResponse.builder()
                .ticketNo(ticket.getTicketNo())
                .typeCode(ticket.getTypeCode())
                .subject(ticket.getSubject())
                .status(ticket.getStatus().name())
                .priority(ticket.getPriority() == null ? null : ticket.getPriority().name())
                .ownerSubjectId(ticket.getOwnerSubjectId())
                .ownerName(ticket.getOwnerName())
                .assigneeGroup(ticket.getAssigneeGroup())
                .assigneeSubjectId(ticket.getAssigneeSubjectId())
                .assigneeName(ticket.getAssigneeName())
                .overdueResponse(ticket.isOverdueResponse())
                .overdueResolution(ticket.isOverdueResolution())
                .lastActivityAt(iso(ticket.getLastActivityAt()))
                .createdAt(iso(ticket.getCreatedAt()))
                .build();
    }

    /**
     * 详情映射。
     *
     * @param ticket 工单
     * @return 详情响应
     */
    public static TicketDetailResponse detailOf(Ticket ticket) {
        TicketDetailResponse detail = new TicketDetailResponse(summaryOf(ticket));
        detail.setDescription(ticket.getDescription());
        detail.setFormAnswers(ticket.getFormAnswers());
        detail.setRelatedTicketNo(ticket.getRelatedTicketNo());
        detail.setResponseDeadline(iso(ticket.getResponseDeadline()));
        detail.setResolutionDeadline(iso(ticket.getResolutionDeadline()));
        detail.setConfirmDeadline(iso(ticket.getConfirmDeadline()));
        detail.setAcceptedAt(iso(ticket.getAcceptedAt()));
        detail.setResolvedAt(iso(ticket.getResolvedAt()));
        detail.setClosedAt(iso(ticket.getClosedAt()));
        detail.setVersion(ticket.getVersion());
        return detail;
    }

    /**
     * 留言映射。
     *
     * @param comment 留言
     * @return 留言响应
     */
    public static CommentResponse commentOf(TicketComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .authorRole(comment.getAuthorRole().name())
                .authorName(comment.getAuthorName())
                .body(comment.getBody())
                .internal(comment.isInternal())
                .createdAt(iso(comment.getCreatedAt()))
                .build();
    }

    /**
     * 类型领域模型转 wire 响应。
     *
     * @param type 类型
     * @return 类型响应
     */
    public static TicketTypeResponse typeOf(TicketType type) {
        return TicketTypeResponse.builder()
                .id(type.getId())
                .code(type.getCode())
                .name(type.getName())
                .formTemplate(type.getFormTemplate())
                .defaultGroup(type.getDefaultGroup())
                .slaResponseMinutes(type.getSlaResponseMinutes())
                .slaResolutionMinutes(type.getSlaResolutionMinutes())
                .enabled(type.isEnabled())
                .build();
    }

    /**
     * 处理组领域模型转 wire 响应。
     *
     * @param group 处理组
     * @return 处理组响应
     */
    public static TicketGroupResponse groupOf(TicketGroup group) {
        return TicketGroupResponse.builder()
                .id(group.getId())
                .code(group.getCode())
                .name(group.getName())
                .memberSubjectIds(group.getMemberSubjectIds())
                .leaderSubjectId(group.getLeaderSubjectId())
                .enabled(group.isEnabled())
                .build();
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
