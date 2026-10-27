package io.github.surezzzzzz.sdk.ticket.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 工单附件（ticket_attachment 表）。commentId nullable——提交期挂单、回复期挂留言（DESIGN §8）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAttachment {

    private Long id;

    /**
     * 所属工单 ID
     */
    private Long ticketId;

    /**
     * 关联留言 ID（null=提交期附件）
     */
    private Long commentId;

    /**
     * 对象存储键（前缀 ticket/{ticketNo}/）
     */
    private String objectKey;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 大小（字节）
     */
    private Long size;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 上传者标识
     */
    private String uploadedBy;

    /**
     * 上传时刻
     */
    private Instant createdAt;
}
