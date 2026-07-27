package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAuditOutcome;
import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提交后发布 KMS 安全审计事件的 Spring 适配器。
 *
 * @author surezzzzzz
 */
public class SpringKmsEventPublisher implements KmsEventPublisher {

    /**
     * Spring 应用事件发布器。
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 创建提交后事件发布适配器。
     *
     * @param applicationEventPublisher Spring 应用事件发布器
     */
    public SpringKmsEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 成功事件只在提交后发布；拒绝和失败事件在事务完成后发布。
     *
     * @param event 已完成且已脱敏的安全审计事件
     */
    @Override
    public void publish(final KmsAuditEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (event.getOutcome() == KmsAuditOutcome.ALLOWED) {
                        publishSafely(event);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    if (event.getOutcome() != KmsAuditOutcome.ALLOWED) {
                        publishSafely(event);
                    }
                }
            });
            return;
        }
        publishSafely(event);
    }

    /**
     * 隔离 listener 侧异常，不能改变已经决定的 KMS 操作结果。
     */
    private void publishSafely(KmsAuditEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (RuntimeException exception) {
            // 审计 listener 故障不得回滚已提交的 KMS 操作。
        }
    }
}
