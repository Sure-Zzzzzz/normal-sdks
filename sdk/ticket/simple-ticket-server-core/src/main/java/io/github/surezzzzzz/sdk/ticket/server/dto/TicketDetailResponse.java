package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 工单详情响应（摘要+描述/答案/SLA 三限/实际时刻/乐观锁版本）。
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class TicketDetailResponse extends TicketSummaryResponse {

    /**
     * 描述 Markdown
     */
    private String description;

    /**
     * 模板答案 JSON 快照
     */
    private String formAnswers;

    /**
     * 关联原单号
     */
    private String relatedTicketNo;

    /**
     * 响应时限
     */
    private String responseDeadline;

    /**
     * 办结时限基线
     */
    private String resolutionDeadline;

    /**
     * 确认期限（RESOLVED 起）
     */
    private String confirmDeadline;

    /**
     * 受理时刻
     */
    private String acceptedAt;

    /**
     * 办结时刻
     */
    private String resolvedAt;

    /**
     * 关闭时刻
     */
    private String closedAt;

    /**
     * 乐观锁版本
     */
    private long version;

    public TicketDetailResponse(TicketSummaryResponse summary) {
        setTicketNo(summary.getTicketNo());
        setTypeCode(summary.getTypeCode());
        setSubject(summary.getSubject());
        setStatus(summary.getStatus());
        setPriority(summary.getPriority());
        setOwnerSubjectId(summary.getOwnerSubjectId());
        setOwnerName(summary.getOwnerName());
        setAssigneeGroup(summary.getAssigneeGroup());
        setAssigneeSubjectId(summary.getAssigneeSubjectId());
        setAssigneeName(summary.getAssigneeName());
        setOverdueResponse(summary.isOverdueResponse());
        setOverdueResolution(summary.isOverdueResolution());
        setLastActivityAt(summary.getLastActivityAt());
        setCreatedAt(summary.getCreatedAt());
    }

    public TicketDetailResponse(String ticketNo, String typeCode, String subject, String status,
                                String priority, String ownerSubjectId, String ownerName,
                                String assigneeGroup, String assigneeSubjectId, String assigneeName,
                                boolean overdueResponse, boolean overdueResolution, String lastActivityAt,
                                String createdAt, String description, String formAnswers,
                                String relatedTicketNo, String responseDeadline, String resolutionDeadline,
                                String confirmDeadline, String acceptedAt, String resolvedAt,
                                String closedAt, long version) {
        super(ticketNo, typeCode, subject, status, priority, ownerSubjectId, ownerName, assigneeGroup,
                assigneeSubjectId, assigneeName, overdueResponse, overdueResolution, lastActivityAt, createdAt);
        this.description = description;
        this.formAnswers = formAnswers;
        this.relatedTicketNo = relatedTicketNo;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
        this.confirmDeadline = confirmDeadline;
        this.acceptedAt = acceptedAt;
        this.resolvedAt = resolvedAt;
        this.closedAt = closedAt;
        this.version = version;
    }

}
