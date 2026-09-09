package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.message.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamMessageEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Web 站内信响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebMessageResponse {

    private Long id;

    private Long recipientUserId;

    private Long senderUserId;

    private String senderUsername;

    private String title;

    private String content;

    private String sendBatchId;

    private String targetSummary;

    private boolean read;

    private Instant readAt;

    private Instant createdAt;

    /**
     * 站内信实体转响应视图
     */
    public static WebMessageResponse from(IamMessageEntity message) {
        return new WebMessageResponse(
                message.getId(),
                message.getRecipientUserId(),
                message.getSenderUserId(),
                message.getSenderUsername(),
                message.getTitle(),
                message.getContent(),
                message.getSendBatchId(),
                message.getTargetSummary(),
                message.getReadAt() != null,
                message.getReadAt(),
                message.getCreatedAt()
        );
    }
}
