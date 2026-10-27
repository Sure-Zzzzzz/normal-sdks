package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties;
import io.github.surezzzzzz.sdk.ticket.server.spi.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知分发（重试 N 次后日志告警，不阻塞主流程——DESIGN §9）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationProvider provider;

    private final SimpleTicketServerProperties properties;

    public NotificationDispatcher(NotificationProvider provider, SimpleTicketServerProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * 分发通知（单收件人便利形态）。
     *
     * @param subjectId 收件人
     * @param title     标题
     * @param content   正文
     */
    public void dispatch(String subjectId, String title, String content) {
        dispatch(java.util.Collections.singletonList(subjectId), title, content);
    }

    /**
     * 分发通知（失败重试，最终失败仅告警）。
     *
     * @param subjectIds 收件人
     * @param title      标题
     * @param content    正文
     */
    public void dispatch(List<String> subjectIds, String title, String content) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return;
        }
        if (!properties.getNotification().isEnabled()) {
            if (log.isDebugEnabled()) {
                log.debug("工单通知已按配置关闭：recipients={}", subjectIds.size());
            }
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            List<String> recipients = new ArrayList<>(subjectIds);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(recipients, title, content);
                }
            });
            return;
        }
        deliver(subjectIds, title, content);
    }

    private void deliver(List<String> subjectIds, String title, String content) {
        int attempts = Math.max(1, properties.getNotification().getRetryTimes());
        for (int i = 1; i <= attempts; i++) {
            try {
                provider.notify(subjectIds, title, content);
                return;
            } catch (Exception e) {
                log.warn("工单通知投递失败（第 {}/{} 次，异常类型={}）",
                        i, attempts, e.getClass().getSimpleName());
            }
        }
        log.error("工单通知最终投递失败（已重试 {} 次，主流程不受影响）", attempts);
    }
}
