package io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 站内信批次收件人（管理台分页行）
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MessageBatchRecipientResponse {

    /**
     * 收件人用户ID
     */
    private Long userId;

    /**
     * 收件人用户名
     */
    private String username;

    /**
     * 收件人展示名（用户已注销时为兜底文案）
     */
    private String displayName;

    /**
     * 读取时间（未读为 null）
     */
    private Instant readAt;
}
