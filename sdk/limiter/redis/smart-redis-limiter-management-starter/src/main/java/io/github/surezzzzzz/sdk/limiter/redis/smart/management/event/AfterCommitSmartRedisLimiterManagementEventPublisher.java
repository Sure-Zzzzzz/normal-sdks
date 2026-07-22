package io.github.surezzzzzz.sdk.limiter.redis.smart.management.event;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 默认事务提交后管理事件发布器
 *
 * @author surezzzzzz
 */
@Slf4j
public class AfterCommitSmartRedisLimiterManagementEventPublisher
        implements SmartRedisLimiterManagementEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 构造事件发布器
     *
     * @param applicationEventPublisher Spring 事件发布器
     */
    public AfterCommitSmartRedisLimiterManagementEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishAfterCommit(SmartRedisLimiterManagementEventPayload payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new SmartRedisLimiterManagementException(
                    ErrorCode.EVENT_REGISTRATION_FAILED,
                    ErrorMessage.EVENT_REGISTRATION_FAILED);
        }
        final SmartRedisLimiterManagementEvent event =
                new SmartRedisLimiterManagementEvent(this, payload);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            applicationEventPublisher.publishEvent(event);
                        } catch (RuntimeException ex) {
                            log.error("发布限流策略管理事件失败，operation={}, revision={}",
                                    payload.getOperation(), payload.getRevision(), ex);
                        }
                    }
                });
    }
}
