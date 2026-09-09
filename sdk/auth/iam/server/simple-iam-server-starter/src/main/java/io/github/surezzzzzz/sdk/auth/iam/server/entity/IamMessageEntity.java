package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 站内信实体
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_message")
public class IamMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "sender_username", length = 64, nullable = false)
    private String senderUsername;

    @Column(name = "title", length = 128, nullable = false)
    private String title;

    @Column(name = "content", length = 2000, nullable = false)
    private String content;

    @Column(name = "send_batch_id", length = 64)
    private String sendBatchId;

    @Column(name = "target_summary", length = 1000)
    private String targetSummary;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
