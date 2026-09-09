package io.github.surezzzzzz.sdk.auth.iam.server.event;

import java.util.Set;

/**
 * 站内信收件人解析完成事件
 *
 * <p>在站内信发送事务成功提交后发布，确保数据库已落库再推送 SSE 未读数。
 *
 * @author surezzzzzz
 */
public class MessageRecipientsResolvedEvent {

    /**
     * 本次发送的所有有效收件人用户 ID
     */
    private final Set<Long> recipientUserIds;

    public MessageRecipientsResolvedEvent(Set<Long> recipientUserIds) {
        this.recipientUserIds = recipientUserIds;
    }

    /**
     * 本次发送解析出的收件用户 ID 集合
     */
    public Set<Long> getRecipientUserIds() {
        return recipientUserIds;
    }
}
