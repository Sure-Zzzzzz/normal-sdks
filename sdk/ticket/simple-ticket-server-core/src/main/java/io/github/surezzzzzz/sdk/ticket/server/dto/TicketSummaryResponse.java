package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单摘要响应（列表/动作返回形态；时间字段 ISO-8601 字符串）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryResponse {

    /**
     * 单号
     */
    private String ticketNo;

    /**
     * 类型编码
     */
    private String typeCode;

    /**
     * 标题
     */
    private String subject;

    /**
     * 状态
     */
    private String status;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 提单人标识
     */
    private String ownerSubjectId;

    /**
     * 提单人姓名快照
     */
    private String ownerName;

    /**
     * 处理组编码
     */
    private String assigneeGroup;

    /**
     * 处理人标识
     */
    private String assigneeSubjectId;

    /**
     * 处理人姓名快照
     */
    private String assigneeName;

    /**
     * 响应超时标记
     */
    private boolean overdueResponse;

    /**
     * 办结超时标记
     */
    private boolean overdueResolution;

    /**
     * 最近活跃时刻
     */
    private String lastActivityAt;

    /**
     * 创建时刻
     */
    private String createdAt;
}
