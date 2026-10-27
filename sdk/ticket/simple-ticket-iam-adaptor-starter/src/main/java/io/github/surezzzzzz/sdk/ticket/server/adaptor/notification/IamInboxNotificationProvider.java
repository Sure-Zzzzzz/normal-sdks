package io.github.surezzzzzz.sdk.ticket.server.adaptor.notification;

import io.github.surezzzzzz.sdk.iam.client.IamMessageClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendRequest;
import io.github.surezzzzzz.sdk.ticket.server.adaptor.annotation.SimpleTicketIamAdaptorComponent;
import io.github.surezzzzzz.sdk.ticket.server.spi.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 站内信通知适配（IAM openapi 站内信族；AKP 授 iam:message:api——DESIGN D4）。
 *
 * <p>投递失败抛给 NotificationDispatcher 统一重试（3 次）后日志告警，不阻塞工单主流程；
 * 离职等无效收件人由服务端按命中数返回，不视为投递失败。</p>
 *
 * @author surezzzzzz
 */
@SimpleTicketIamAdaptorComponent
public class IamInboxNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(IamInboxNotificationProvider.class);

    private final IamMessageClient messageClient;

    public IamInboxNotificationProvider(IamMessageClient messageClient) {
        this.messageClient = messageClient;
    }

    @Override
    public void notify(List<String> subjectIds, String title, String content) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return;
        }
        io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendResult result =
                messageClient.sendMessage(IamMessageSendRequest.builder()
                        .recipientSubjectIds(subjectIds)
                        .title(title)
                        .content(content)
                        .build());
        if (log.isDebugEnabled()) {
            log.debug("工单站内信已投递：recipients={}, 命中={}",
                    subjectIds.size(), result == null ? -1 : result.getRecipientCount());
        }
    }
}
