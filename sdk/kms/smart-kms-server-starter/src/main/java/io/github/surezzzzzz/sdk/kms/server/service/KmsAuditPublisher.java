package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsEventPublisher;

import java.util.HashMap;
import java.util.Map;

/**
 * KMS 成功操作审计事件构造与发布器。
 *
 * @author surezzzzzz
 */
public class KmsAuditPublisher {

    private final KmsClock clock;
    private final KmsEventPublisher eventPublisher;

    /**
     * 创建安全审计发布器。
     */
    public KmsAuditPublisher(KmsClock clock, KmsEventPublisher eventPublisher) {
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布已允许操作的无敏感审计事件。
     */
    public void allowed(KmsPrincipal principal, String keyRef, Integer keyVersion, KmsOperation operation,
                        String requestId, String resourceType, KmsKeyState keyState,
                        KmsKeyVersionState versionState, Integer inputLength, Integer outputLength) {
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE, resourceType);
        if (keyState != null) {
            metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_KEY_STATE, keyState.getCode());
        }
        if (versionState != null) {
            metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_VERSION_STATE, versionState.getCode());
        }
        if (inputLength != null) {
            metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_INPUT_LENGTH, inputLength.toString());
        }
        if (outputLength != null) {
            metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_OUTPUT_LENGTH, outputLength.toString());
        }
        publish(principal, keyRef, keyVersion, operation, requestId, KmsAuditOutcome.ALLOWED, metadata);
    }

    /**
     * 尽力发布已持久化管理结果的幂等重放事件。
     */
    public void replayed(KmsPrincipal principal, String keyRef, KmsOperation operation, String requestId,
                         String resourceType) {
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE, resourceType);
        metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_IDEMPOTENCY_REPLAY,
                SmartKmsCoreConstant.AUDIT_BOOLEAN_TRUE);
        publish(principal, keyRef, null, operation, requestId, KmsAuditOutcome.ALLOWED, metadata);
    }

    /**
     * 尽力发布被规则拒绝的无敏感审计事件。
     */
    public void rejected(KmsPrincipal principal, String keyRef, Integer keyVersion, KmsOperation operation,
                         String requestId, String failureCategory) {
        publishFailure(principal, keyRef, keyVersion, operation, requestId, KmsAuditOutcome.REJECTED,
                failureCategory);
    }

    /**
     * 尽力发布执行失败的无敏感审计事件。
     */
    public void failed(KmsPrincipal principal, String keyRef, Integer keyVersion, KmsOperation operation,
                       String requestId, String failureCategory) {
        publishFailure(principal, keyRef, keyVersion, operation, requestId, KmsAuditOutcome.FAILED, failureCategory);
    }

    /**
     * 构造仅含失败分类的审计元数据。
     */
    private void publishFailure(KmsPrincipal principal, String keyRef, Integer keyVersion, KmsOperation operation,
                                String requestId, KmsAuditOutcome outcome, String failureCategory) {
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY, failureCategory);
        publish(principal, keyRef, keyVersion, operation, requestId, outcome, metadata);
    }

    /**
     * 事件发布失败不得覆盖原有 KMS 执行结果。
     */
    private void publish(KmsPrincipal principal, String keyRef, Integer keyVersion, KmsOperation operation,
                         String requestId, KmsAuditOutcome outcome, Map<String, String> metadata) {
        try {
            eventPublisher.publish(KmsAuditEvent.builder().tenantId(principal.getTenantId()).keyRef(keyRef)
                    .keyVersion(keyVersion).principalId(principal.getPrincipalId()).operation(operation)
                    .outcome(outcome).requestId(requestId).occurredAt(clock.now()).metadata(metadata).build());
        } catch (RuntimeException exception) {
            // 审计发布故障不得影响 KMS 主操作。
        }
    }
}
