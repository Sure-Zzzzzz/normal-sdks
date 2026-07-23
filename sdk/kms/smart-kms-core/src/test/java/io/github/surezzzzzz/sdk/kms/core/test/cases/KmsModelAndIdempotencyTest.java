package io.github.surezzzzzz.sdk.kms.core.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAuditOutcome;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
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

    @Test
    void shouldDefensivelyCopyAllBinaryModelsAndAuditMetadata() {
        log.info("校验二进制模型和审计元数据防御性拷贝");
        byte[] privateMaterial = new byte[]{7, 9};
        byte[] publicMaterial = new byte[]{11, 13};
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("result", "success");

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
                .occurredAt(Instant.parse("2026-07-23T00:00:00Z"))
                .metadata(metadata)
                .build();

        privateMaterial[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        publicMaterial[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        metadata.put("result", "changed");
        byte[] privateRead = keyVersion.getPrivateMaterial();
        privateRead[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        byte[] publicRead = publicKey.getPublicMaterial();
        publicRead[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;

        Assertions.assertEquals(7, keyVersion.getPrivateMaterial()[SmartKmsCoreConstant.ZERO]);
        Assertions.assertEquals(11, keyVersion.getPublicMaterial()[SmartKmsCoreConstant.ZERO]);
        Assertions.assertEquals(11, publicKey.getPublicMaterial()[SmartKmsCoreConstant.ZERO]);
        Assertions.assertEquals("success", auditEvent.getMetadata().get("result"));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> auditEvent.getMetadata().put("result", "changed"));
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
