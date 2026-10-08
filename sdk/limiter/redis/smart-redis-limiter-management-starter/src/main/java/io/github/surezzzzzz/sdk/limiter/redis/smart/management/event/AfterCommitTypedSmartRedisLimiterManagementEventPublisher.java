package io.github.surezzzzzz.sdk.limiter.redis.smart.management.event;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.DefaultSmartRedisLimiterTypedPolicyManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 默认事务提交后类型化管理事件发布器
 *
 * @author surezzzzzz
 */
@Slf4j
public class AfterCommitTypedSmartRedisLimiterManagementEventPublisher
        implements DefaultSmartRedisLimiterTypedPolicyManagementService.TypedEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 构造类型化事件发布器
     *
     * @param applicationEventPublisher Spring 事件发布器
     */
    public AfterCommitTypedSmartRedisLimiterManagementEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(SmartRedisLimiterTypedManagementEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new SmartRedisLimiterManagementException(
                    ErrorCode.EVENT_REGISTRATION_FAILED,
                    ErrorMessage.EVENT_REGISTRATION_FAILED);
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            log.debug("发布已提交类型化策略管理事件 operation={}, revision={}",
                                    event.getPayload().getOperation(), event.getPayload().getRevision());
                            applicationEventPublisher.publishEvent(event);
                        } catch (RuntimeException ex) {
                            log.error("发布类型化限流策略管理事件失败，operation={}, revision={}",
                                    event.getPayload().getOperation(), event.getPayload().getRevision(), ex);
                        }
                    }
                });
    }
}
