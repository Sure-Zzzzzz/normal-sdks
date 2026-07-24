package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.*;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全审计事件。
 *
 * <p>该模型只能携带审计红线允许的主体、tenant、资源、操作、结果、时间、请求标识和安全元数据。
 * metadata 仅接受固定白名单和值格式；密码材料、明文、密文、签名、AAD、请求正文、凭据、SQL、
 * Provider 细节和异常链均不能通过本模型边界。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsAuditEvent {

    /**
     * 发生操作的 tenant。
     */
    private final String tenantId;
    /**
     * 操作关联的逻辑密钥标识；创建密钥在分配标识前被拒绝或失败时为 {@code null}。
     */
    private final String keyRef;
    /**
     * 操作关联的密钥版本；最终销毁必须为正版本号，其他不关联具体版本的事件为 {@code null}。
     */
    private final Integer keyVersion;
    /**
     * 发起操作的认证主体标识。
     */
    private final String principalId;
    /**
     * 已执行或尝试执行的 KMS 操作。
     */
    private final KmsOperation operation;
    /**
     * 操作审计结果。
     */
    private final KmsAuditOutcome outcome;
    /**
     * 与调用链关联的请求标识。
     */
    private final String requestId;
    /**
     * 审计事件发生时间。
     */
    private final Instant occurredAt;
    /**
     * 经调用方安全筛选后的不可变元数据。
     */
    private final Map<String, String> metadata;

    /**
     * 创建安全审计事件并防御性复制元数据。
     *
     * @param tenantId    操作 tenant
     * @param keyRef      逻辑密钥标识；仅创建密钥在分配标识前被拒绝或失败时可与版本一同为 {@code null}
     * @param keyVersion  密钥版本；最终销毁必须为正整数，未关联具体版本时为 {@code null}
     * @param principalId 认证主体标识
     * @param operation   KMS 操作
     * @param outcome     审计结果
     * @param requestId   请求标识
     * @param occurredAt  发生时间
     * @param metadata    已按审计红线筛选的安全元数据，可为 {@code null}
     */
    @Builder
    public KmsAuditEvent(String tenantId, String keyRef, Integer keyVersion, String principalId,
                         KmsOperation operation, KmsAuditOutcome outcome, String requestId,
                         Instant occurredAt, Map<String, String> metadata) {
        this.tenantId = KmsValidationHelper.requireTenantId(tenantId);
        this.keyRef = keyRef == null ? null : KmsValidationHelper.requireKeyRef(keyRef);
        if (keyVersion != null && keyVersion.intValue() <= SmartKmsCoreConstant.ZERO) {
            throw new KmsValidationException();
        }
        this.keyVersion = keyVersion;
        this.principalId = KmsValidationHelper.requirePrincipalId(principalId);
        if (operation == null || outcome == null || occurredAt == null) {
            throw new KmsValidationException();
        }
        this.operation = operation;
        this.outcome = outcome;
        this.requestId = KmsValidationHelper.requireRequestId(requestId);
        this.occurredAt = occurredAt;
        validateResourceReference();
        validateSystemPrincipal();
        this.metadata = validateAndCopyMetadata(metadata);
        validateFailureCategory();
    }

    private static Map<String, String> validateAndCopyMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return Collections.emptyMap();
        }
        if (metadata.size() > SmartKmsCoreConstant.AUDIT_METADATA_MAX_ENTRIES) {
            throw new KmsValidationException();
        }
        Map<String, String> copy = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!isMetadataValueValid(key, value)) {
                throw new KmsValidationException();
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean isMetadataValueValid(String key, String value) {
        if (!isMetadataTextValid(key) || !isMetadataTextValid(value)) {
            return false;
        }
        if (SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE.equals(key)) {
            return isResourceType(value);
        }
        if (SmartKmsCoreConstant.AUDIT_METADATA_KEY_KEY_STATE.equals(key)) {
            return KmsKeyState.isValid(value);
        }
        if (SmartKmsCoreConstant.AUDIT_METADATA_KEY_VERSION_STATE.equals(key)) {
            return KmsKeyVersionState.isValid(value);
        }
        if (SmartKmsCoreConstant.AUDIT_METADATA_KEY_INPUT_LENGTH.equals(key)
                || SmartKmsCoreConstant.AUDIT_METADATA_KEY_OUTPUT_LENGTH.equals(key)) {
            return isCanonicalNonNegativeDecimal(value);
        }
        if (SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY.equals(key)) {
            return isFailureCategory(value);
        }
        if (SmartKmsCoreConstant.AUDIT_METADATA_KEY_IDEMPOTENCY_REPLAY.equals(key)) {
            return SmartKmsCoreConstant.AUDIT_BOOLEAN_TRUE.equals(value)
                    || SmartKmsCoreConstant.AUDIT_BOOLEAN_FALSE.equals(value);
        }
        return false;
    }

    private static boolean isMetadataTextValid(String value) {
        if (value == null || value.isEmpty()
                || value.length() > SmartKmsCoreConstant.AUDIT_METADATA_VALUE_MAX_LENGTH) {
            return false;
        }
        for (int index = SmartKmsCoreConstant.ZERO; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index)) || Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isResourceType(String value) {
        return SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY.equals(value)
                || SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_POLICY.equals(value)
                || SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION.equals(value)
                || SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_DESTRUCTION_JOB.equals(value);
    }

    private static boolean isFailureCategory(String value) {
        return SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_NOT_FOUND.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_POLICY_CONFLICT.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_IDEMPOTENCY_CONFLICT.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC.equals(value)
                || SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE.equals(value);
    }

    private static boolean isCanonicalNonNegativeDecimal(String value) {
        if (SmartKmsCoreConstant.ZERO == value.length()) {
            return false;
        }
        if (value.length() > SmartKmsCoreConstant.ONE && value.charAt(SmartKmsCoreConstant.ZERO) == '0') {
            return false;
        }
        for (int index = SmartKmsCoreConstant.ZERO; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private void validateResourceReference() {
        if (keyRef == null) {
            if (keyVersion != null || operation != KmsOperation.CREATE_KEY || outcome == KmsAuditOutcome.ALLOWED) {
                throw new KmsValidationException();
            }
            return;
        }
        if (operation == KmsOperation.PROCESS_KEY_DESTRUCTION && keyVersion == null) {
            throw new KmsValidationException();
        }
    }

    private void validateSystemPrincipal() {
        if (operation == KmsOperation.PROCESS_KEY_DESTRUCTION
                && !SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID.equals(principalId)) {
            throw new KmsValidationException();
        }
        if (operation != KmsOperation.PROCESS_KEY_DESTRUCTION
                && SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID.equals(principalId)) {
            throw new KmsValidationException();
        }
    }

    private void validateFailureCategory() {
        boolean hasFailureCategory = metadata.containsKey(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY);
        if ((outcome == KmsAuditOutcome.ALLOWED && hasFailureCategory)
                || (outcome != KmsAuditOutcome.ALLOWED && !hasFailureCategory)) {
            throw new KmsValidationException();
        }
    }

    /**
     * 获取不可变的安全元数据快照。
     *
     * @return 不可修改的元数据集合
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
