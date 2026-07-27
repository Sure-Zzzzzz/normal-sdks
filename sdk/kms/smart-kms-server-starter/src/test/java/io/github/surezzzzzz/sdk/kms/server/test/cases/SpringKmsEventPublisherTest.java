package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAuditOutcome;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;
import io.github.surezzzzzz.sdk.kms.server.service.SpringKmsEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Spring KMS 审计事件发布时序测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SpringKmsEventPublisherTest {

    /**
     * 构造无敏感信息的审计事件。
     *
     * @param outcome 审计结果
     * @return 审计事件
     */
    private static KmsAuditEvent event(KmsAuditOutcome outcome) {
        java.util.Map<String, String> metadata = outcome == KmsAuditOutcome.ALLOWED
                ? Collections.singletonMap(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY)
                : Collections.singletonMap(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY,
                SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
        return KmsAuditEvent.builder().tenantId("test-tenant").keyRef("test-key-ref")
                .principalId("test-principal").operation(KmsOperation.CREATE_KEY).outcome(outcome)
                .requestId("test-request-id-000000000001").occurredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .metadata(metadata).build();
    }

    /**
     * 清理当前线程注册的事务同步，避免污染相邻用例。
     */
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /**
     * 验证允许事件只在事务提交后发布。
     */
    @Test
    void shouldPublishAllowedEventOnlyAfterCommit() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        SpringKmsEventPublisher publisher = new SpringKmsEventPublisher(applicationEventPublisher);
        KmsAuditEvent event = event(KmsAuditOutcome.ALLOWED);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publish(event);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        log.info("允许事件已注册事务同步，数量: {}", synchronizations.size());
        assertEquals(Integer.valueOf(1), Integer.valueOf(synchronizations.size()), "允许事件必须注册一个事务同步");
        verify(applicationEventPublisher, never()).publishEvent(event);

        synchronizations.get(0).afterCommit();
        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        log.info("允许事件提交后已发布");
        verify(applicationEventPublisher).publishEvent(event);
    }

    /**
     * 验证拒绝事件在回滚完成后发布，而不是等待提交。
     */
    @Test
    void shouldPublishRejectedEventAfterRollbackCompletion() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        SpringKmsEventPublisher publisher = new SpringKmsEventPublisher(applicationEventPublisher);
        KmsAuditEvent event = event(KmsAuditOutcome.REJECTED);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publish(event);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        verify(applicationEventPublisher, never()).publishEvent(event);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        log.info("拒绝事件回滚完成后已发布");
        verify(applicationEventPublisher).publishEvent(event);
    }

    /**
     * 验证 listener 故障不会影响已经决定的事件发布路径。
     */
    @Test
    void shouldIsolateEventListenerFailure() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        org.mockito.Mockito.doThrow(new RuntimeException()).when(applicationEventPublisher).publishEvent(org.mockito.ArgumentMatchers.any());
        SpringKmsEventPublisher publisher = new SpringKmsEventPublisher(applicationEventPublisher);
        KmsAuditEvent event = event(KmsAuditOutcome.FAILED);

        publisher.publish(event);
        log.info("listener 抛出异常后的失败事件发布调用已完成");
        verify(applicationEventPublisher, times(1)).publishEvent(event);
    }
}
