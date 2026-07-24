package io.github.surezzzzzz.sdk.kms.core.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.*;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsIdempotencyConflictException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;
import io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.core.support.KmsIdempotencyHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsKeyMaterialHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * KMS 模型和幂等规则测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsModelAndIdempotencyTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PRINCIPAL_ID = "principal-a";
    private static final String KEY_REF = "key-ref-a";
    private static final String ENDPOINT = "key-create";
    private static final String IDEMPOTENCY_KEY = "idempotency-a";
    private static final String REQUEST_HASH = "request-hash-a";
    private static final String REQUEST_ID = "request-a";

    @Test
    void shouldDefensivelyCopyAllBinaryModelsAndAuditMetadata() {
        log.info("校验二进制模型和审计元数据防御性拷贝");
        byte[] privateMaterial = new byte[]{7, 9};
        byte[] publicMaterial = new byte[]{11, 13};
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY);

        KmsKeyVersion keyVersion = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.ES256, KmsKeyVersionState.ACTIVE,
                null, privateMaterial, null, publicMaterial, null);
        KmsPublicKey publicKey = new KmsPublicKey(KEY_REF, SmartKmsCoreConstant.ONE,
                KmsAlgorithm.ES256, KmsKeyVersionState.ACTIVE, publicMaterial);
        KmsAuditEvent auditEvent = KmsAuditEvent.builder()
                .tenantId(TENANT_ID)
                .keyRef(KEY_REF)
                .principalId(PRINCIPAL_ID)
                .operation(KmsOperation.SIGN)
                .outcome(KmsAuditOutcome.ALLOWED)
                .requestId(REQUEST_ID)
                .occurredAt(Instant.parse("2026-07-23T00:00:00Z"))
                .metadata(metadata)
                .build();

        privateMaterial[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        publicMaterial[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        metadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_POLICY);
        byte[] privateRead = keyVersion.getPrivateMaterial();
        privateRead[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        byte[] publicRead = publicKey.getPublicMaterial();
        publicRead[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;

        Assertions.assertEquals(7, keyVersion.getPrivateMaterial()[SmartKmsCoreConstant.ZERO]);
        Assertions.assertEquals(11, keyVersion.getPublicMaterial()[SmartKmsCoreConstant.ZERO]);
        Assertions.assertEquals(11, publicKey.getPublicMaterial()[SmartKmsCoreConstant.ZERO]);
        Assertions.assertEquals(SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY,
                auditEvent.getMetadata().get(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> auditEvent.getMetadata().put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                        SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_POLICY));
        Assertions.assertTrue(keyVersion.toString().contains(KEY_REF));
        Assertions.assertFalse(keyVersion.toString().contains("privateMaterial"));
        Assertions.assertFalse(keyVersion.toString().contains("symmetricMaterial"));
        Assertions.assertFalse(keyVersion.toString().contains("publicMaterial"));
        Assertions.assertFalse(keyVersion.toString().contains("[7, 9]"));
        Assertions.assertFalse(keyVersion.toString().contains("[11, 13]"));
        Assertions.assertFalse(publicKey.toString().contains("publicMaterial"));
        Assertions.assertFalse(publicKey.toString().contains("[11, 13]"));
    }

    @Test
    void shouldAcceptOnlyWhitelistedAuditMetadata() {
        log.info("校验审计元数据白名单和值格式");
        Map<String, String> validMetadata = new HashMap<String, String>();
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION);
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_KEY_STATE,
                KmsKeyState.ACTIVE.getCode());
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_VERSION_STATE,
                KmsKeyVersionState.RETIRED.getCode());
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_INPUT_LENGTH, "0");
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_OUTPUT_LENGTH,
                "42949672951234567890123456789012");
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY,
                SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC);
        validMetadata.put(SmartKmsCoreConstant.AUDIT_METADATA_KEY_IDEMPOTENCY_REPLAY,
                SmartKmsCoreConstant.AUDIT_BOOLEAN_FALSE);

        KmsAuditEvent event = createAuditEvent(TENANT_ID, KEY_REF, PRINCIPAL_ID,
                KmsOperation.SIGN, KmsAuditOutcome.FAILED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z"), validMetadata);
        Assertions.assertEquals(SmartKmsCoreConstant.AUDIT_METADATA_MAX_ENTRIES,
                event.getMetadata().size(), "合法白名单元数据应完整保留");
        Assertions.assertTrue(createAuditEvent(null).getMetadata().isEmpty(),
                "空元数据应转换为空的不可变快照");

        Map<String, String> tooManyMetadata = new HashMap<String, String>(validMetadata);
        tooManyMetadata.put("unexpected", "value");
        assertMetadataRejected(tooManyMetadata);
        assertMetadataRejected(singleMetadata("plaintext", "value"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                "UNKNOWN"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_KEY_STATE,
                "UNKNOWN"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_VERSION_STATE,
                "UNKNOWN"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_INPUT_LENGTH, "01"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_INPUT_LENGTH, "-1"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_OUTPUT_LENGTH, "1.0"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY,
                "exception"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_IDEMPOTENCY_REPLAY,
                "TRUE"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE, " "));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                "KEY\nVERSION"));
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                repeat("a", SmartKmsCoreConstant.AUDIT_METADATA_VALUE_MAX_LENGTH
                        + SmartKmsCoreConstant.ONE)));

        Map<String, String> nullKeyMetadata = new HashMap<String, String>();
        nullKeyMetadata.put(null, SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY);
        assertMetadataRejected(nullKeyMetadata);
        assertMetadataRejected(singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE, null));
    }

    @Test
    void shouldRequireCompleteAuditEventAndFixedWorkerPrincipal() {
        log.info("校验审计事件必填字段和销毁 worker 系统主体");
        Assertions.assertDoesNotThrow(() -> createAuditEvent(singleMetadata(
                SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY)));
        assertAuditEventRejected(null, KEY_REF, PRINCIPAL_ID, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, null, PRINCIPAL_ID, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        Assertions.assertDoesNotThrow(() -> createAuditEvent(TENANT_ID, null, PRINCIPAL_ID,
                KmsOperation.CREATE_KEY, KmsAuditOutcome.REJECTED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z")));
        Assertions.assertDoesNotThrow(() -> createAuditEvent(TENANT_ID, null, PRINCIPAL_ID,
                KmsOperation.CREATE_KEY, KmsAuditOutcome.FAILED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z")));
        assertAuditEventRejected(TENANT_ID, null, PRINCIPAL_ID, KmsOperation.CREATE_KEY,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        Assertions.assertDoesNotThrow(() -> createAuditEvent(TENANT_ID, KEY_REF, PRINCIPAL_ID,
                KmsOperation.CREATE_KEY, KmsAuditOutcome.ALLOWED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z")));
        assertAuditEventRejected(TENANT_ID, KEY_REF, null, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, KEY_REF, PRINCIPAL_ID, null,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, KEY_REF, PRINCIPAL_ID, KmsOperation.SIGN,
                null, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, KEY_REF, PRINCIPAL_ID, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, null, Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, KEY_REF, PRINCIPAL_ID, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, null);
        Assertions.assertThrows(KmsValidationException.class, () -> KmsAuditEvent.builder()
                .tenantId(TENANT_ID)
                .keyRef(KEY_REF)
                .keyVersion(SmartKmsCoreConstant.ZERO)
                .principalId(PRINCIPAL_ID)
                .operation(KmsOperation.SIGN)
                .outcome(KmsAuditOutcome.ALLOWED)
                .requestId(REQUEST_ID)
                .occurredAt(Instant.parse("2026-07-23T00:00:00Z"))
                .build(), "审计版本必须为正整数");
        assertAuditEventRejected(TENANT_ID, KEY_REF, PRINCIPAL_ID,
                KmsOperation.PROCESS_KEY_DESTRUCTION, KmsAuditOutcome.ALLOWED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        assertAuditEventRejected(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID, KmsOperation.PROCESS_KEY_DESTRUCTION,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"));
        Assertions.assertDoesNotThrow(() -> createAuditEvent(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                KmsOperation.PROCESS_KEY_DESTRUCTION, KmsAuditOutcome.ALLOWED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z"), singleMetadata(
                        SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                        SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION)));
        Assertions.assertThrows(KmsValidationException.class, () -> createAuditEvent(TENANT_ID, null,
                        SmartKmsCoreConstant.ONE, PRINCIPAL_ID, KmsOperation.CREATE_KEY,
                        KmsAuditOutcome.REJECTED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"),
                        singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY,
                                SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION)),
                "未分配 keyRef 的创建失败不得关联版本");
    }

    @Test
    void shouldRequireFailureCategoryOnlyForNonAllowedAuditEvent() {
        log.info("校验审计结果与失败类别一致性");
        Assertions.assertDoesNotThrow(() -> createAuditEvent(TENANT_ID, KEY_REF, PRINCIPAL_ID,
                KmsOperation.SIGN, KmsAuditOutcome.REJECTED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z")));
        Assertions.assertDoesNotThrow(() -> createAuditEvent(TENANT_ID, KEY_REF, PRINCIPAL_ID,
                KmsOperation.SIGN, KmsAuditOutcome.FAILED, REQUEST_ID,
                Instant.parse("2026-07-23T00:00:00Z")));
        assertAuditEventMetadataRejected(KmsAuditOutcome.REJECTED, null);
        assertAuditEventMetadataRejected(KmsAuditOutcome.FAILED, null);
        assertAuditEventMetadataRejected(KmsAuditOutcome.ALLOWED, singleMetadata(
                SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY,
                SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC));
    }

    @Test
    void shouldValidateAlgorithmSpecificKeyMaterial() {
        log.info("校验 ES256 和 AES-256-GCM 材料规则");
        KmsKeyVersion es256 = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.ES256, KmsKeyVersionState.ACTIVE,
                null, new byte[]{SmartKmsCoreConstant.ONE}, null,
                new byte[]{SmartKmsCoreConstant.ASN1_INTEGER_TAG}, null);
        KmsKeyVersion aes = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.AES_256_GCM,
                KmsKeyVersionState.ACTIVE, null, null,
                new byte[SmartKmsCoreConstant.AES_256_KEY_LENGTH], null, null);
        KmsKeyVersion invalidEs256 = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.ES256, KmsKeyVersionState.ACTIVE,
                null, null, null, new byte[]{SmartKmsCoreConstant.ONE}, null);
        KmsKeyVersion invalidAes = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.AES_256_GCM,
                KmsKeyVersionState.ACTIVE, null, null,
                new byte[SmartKmsCoreConstant.AES_256_KEY_LENGTH - SmartKmsCoreConstant.ONE], null, null);

        Assertions.assertDoesNotThrow(() -> KmsKeyMaterialHelper.validate(es256));
        Assertions.assertDoesNotThrow(() -> KmsKeyMaterialHelper.validate(aes));
        Assertions.assertThrows(io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException.class,
                () -> KmsKeyMaterialHelper.validate(invalidEs256));
        Assertions.assertThrows(io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException.class,
                () -> KmsKeyMaterialHelper.validate(invalidAes));
    }

    @Test
    void shouldAllowOnlyMaterialFreeDestroyedVersionWithDestructionTime() {
        log.info("校验已销毁版本材料和时间边界");
        Instant destroyedAt = Instant.parse("2026-07-23T00:00:00Z");
        KmsKeyVersion destroyed = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.ES256, KmsKeyVersionState.DESTROYED,
                KmsKeyVersionState.ACTIVE, null, null, null, destroyedAt);
        KmsKeyVersion missingDestroyedAt = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.ES256, KmsKeyVersionState.DESTROYED,
                KmsKeyVersionState.ACTIVE, null, null, null, null);
        KmsKeyVersion destroyedWithMaterial = new KmsKeyVersion(TENANT_ID, KEY_REF,
                SmartKmsCoreConstant.ONE, KmsAlgorithm.AES_256_GCM, KmsKeyVersionState.DESTROYED,
                KmsKeyVersionState.ACTIVE, null, new byte[SmartKmsCoreConstant.AES_256_KEY_LENGTH],
                null, destroyedAt);

        Assertions.assertDoesNotThrow(() -> KmsKeyMaterialHelper.validate(destroyed));
        Assertions.assertEquals(destroyedAt, destroyed.getDestroyedAt());
        Assertions.assertThrows(io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException.class,
                () -> KmsKeyMaterialHelper.validate(missingDestroyedAt));
        Assertions.assertThrows(io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException.class,
                () -> KmsKeyMaterialHelper.validate(destroyedWithMaterial));
    }

    @Test
    void shouldReplayOnlyExactIdempotencyRecordAndRejectHashConflict() {
        log.info("校验幂等重放和摘要冲突");
        KmsIdempotencyRecord record = KmsIdempotencyRecord.builder()
                .tenantId(TENANT_ID)
                .principalId(PRINCIPAL_ID)
                .endpoint(ENDPOINT)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .requestHash(REQUEST_HASH)
                .build();

        Assertions.assertTrue(KmsIdempotencyHelper.isReplayable(record, TENANT_ID,
                PRINCIPAL_ID, ENDPOINT, IDEMPOTENCY_KEY, REQUEST_HASH));
        Assertions.assertFalse(KmsIdempotencyHelper.isReplayable(record, "tenant-b",
                PRINCIPAL_ID, ENDPOINT, IDEMPOTENCY_KEY, REQUEST_HASH));
        Assertions.assertFalse(KmsIdempotencyHelper.isReplayable(record, TENANT_ID,
                "principal-b", ENDPOINT, IDEMPOTENCY_KEY, REQUEST_HASH));
        Assertions.assertFalse(KmsIdempotencyHelper.isReplayable(record, TENANT_ID,
                PRINCIPAL_ID, "key-rotate", IDEMPOTENCY_KEY, REQUEST_HASH));
        Assertions.assertFalse(KmsIdempotencyHelper.isReplayable(null, TENANT_ID,
                PRINCIPAL_ID, ENDPOINT, IDEMPOTENCY_KEY, REQUEST_HASH));
        Assertions.assertThrows(KmsIdempotencyConflictException.class,
                () -> KmsIdempotencyHelper.isReplayable(record, TENANT_ID, PRINCIPAL_ID,
                        ENDPOINT, IDEMPOTENCY_KEY, "request-hash-b"));
    }

    private KmsAuditEvent createAuditEvent(Map<String, String> metadata) {
        return createAuditEvent(TENANT_ID, KEY_REF, PRINCIPAL_ID, KmsOperation.SIGN,
                KmsAuditOutcome.ALLOWED, REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"), metadata);
    }

    private KmsAuditEvent createAuditEvent(String tenantId, String keyRef, String principalId,
                                           KmsOperation operation, KmsAuditOutcome outcome,
                                           String requestId, Instant occurredAt) {
        return createAuditEvent(tenantId, keyRef, principalId, operation, outcome, requestId, occurredAt,
                defaultAuditMetadata(outcome));
    }

    private Map<String, String> defaultAuditMetadata(KmsAuditOutcome outcome) {
        if (outcome == KmsAuditOutcome.ALLOWED) {
            return singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_RESOURCE_TYPE,
                    SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY);
        }
        return singleMetadata(SmartKmsCoreConstant.AUDIT_METADATA_KEY_FAILURE_CATEGORY,
                SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
    }

    private KmsAuditEvent createAuditEvent(String tenantId, String keyRef, String principalId,
                                           KmsOperation operation, KmsAuditOutcome outcome,
                                           String requestId, Instant occurredAt,
                                           Map<String, String> metadata) {
        return createAuditEvent(tenantId, keyRef, null, principalId, operation, outcome, requestId,
                occurredAt, metadata);
    }

    private KmsAuditEvent createAuditEvent(String tenantId, String keyRef, Integer keyVersion,
                                           String principalId, KmsOperation operation,
                                           KmsAuditOutcome outcome, String requestId, Instant occurredAt,
                                           Map<String, String> metadata) {
        return KmsAuditEvent.builder()
                .tenantId(tenantId)
                .keyRef(keyRef)
                .keyVersion(keyVersion)
                .principalId(principalId)
                .operation(operation)
                .outcome(outcome)
                .requestId(requestId)
                .occurredAt(occurredAt)
                .metadata(metadata)
                .build();
    }

    private void assertAuditEventRejected(String tenantId, String keyRef, String principalId,
                                          KmsOperation operation, KmsAuditOutcome outcome,
                                          String requestId, Instant occurredAt) {
        KmsValidationException exception = Assertions.assertThrows(KmsValidationException.class,
                () -> createAuditEvent(tenantId, keyRef, principalId, operation, outcome, requestId,
                        occurredAt), "缺失或伪造审计骨架字段必须被拒绝");
        Assertions.assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    private Map<String, String> singleMetadata(String key, String value) {
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(key, value);
        return metadata;
    }

    private void assertMetadataRejected(Map<String, String> metadata) {
        KmsValidationException exception = Assertions.assertThrows(KmsValidationException.class,
                () -> createAuditEvent(metadata), "非法审计元数据必须被拒绝");
        Assertions.assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode(),
                "非法审计元数据必须使用稳定参数错误码");
    }

    private void assertAuditEventMetadataRejected(KmsAuditOutcome outcome, Map<String, String> metadata) {
        KmsValidationException exception = Assertions.assertThrows(KmsValidationException.class,
                () -> createAuditEvent(TENANT_ID, KEY_REF, PRINCIPAL_ID, KmsOperation.SIGN, outcome,
                        REQUEST_ID, Instant.parse("2026-07-23T00:00:00Z"), metadata),
                "审计结果与失败类别不一致时必须被拒绝");
        Assertions.assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode(),
                "审计结果与失败类别不一致时必须使用稳定参数错误码");
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = SmartKmsCoreConstant.ZERO; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    @Test
    void shouldExposeStableEnumAndExceptionContracts() {
        log.info("校验枚举和异常稳定契约");

        Assertions.assertEquals(KmsKeyState.ACTIVE, KmsKeyState.fromCode("ACTIVE"));
        Assertions.assertFalse(KmsKeyState.isValid("UNKNOWN"));
        Assertions.assertEquals(KmsOperation.READ_PUBLIC_KEY,
                KmsOperation.fromCode(KmsOperation.READ_PUBLIC_KEY.getCode()));
        Assertions.assertEquals(KmsAlgorithm.ES256, KmsAlgorithm.fromCode("ES256"));
        Assertions.assertEquals(KmsKeyVersionState.DESTROYED,
                KmsKeyVersionState.fromCode("DESTROYED"));
        Assertions.assertEquals(ErrorCode.VALIDATION_FAILED,
                new KmsValidationException().getErrorCode());
    }
}
