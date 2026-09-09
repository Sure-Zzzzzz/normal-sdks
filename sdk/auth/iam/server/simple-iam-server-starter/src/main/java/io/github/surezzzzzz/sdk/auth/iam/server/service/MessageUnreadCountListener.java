package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.event.MessageRecipientsResolvedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 站内信未读数提交后推送监听器
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class MessageUnreadCountListener {

    private final MessageService messageService;
    private final MessageSseService messageSseService;

    /**
     * 消息落地后向收件人推送未读数刷新广播
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageRecipientsResolved(MessageRecipientsResolvedEvent event) {
        for (Long recipientUserId : event.getRecipientUserIds()) {
            messageSseService.pushUnreadCount(recipientUserId,
                    messageService.countUnreadMessages(recipientUserId));
        }
    }
}
