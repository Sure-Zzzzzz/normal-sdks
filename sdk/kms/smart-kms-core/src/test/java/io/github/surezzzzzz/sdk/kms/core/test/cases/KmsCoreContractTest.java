package io.github.surezzzzzz.sdk.kms.core.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.support.KmsAuthorizationHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsStateHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

/**
 * KMS core 领域契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsCoreContractTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PRINCIPAL_ID = "principal-a";
    private static final String KEY_REF = "key-ref-a";
    private static final String POLICY_ID = "policy-a";
    private static final int VERSION = SmartKmsCoreConstant.ONE;

    @Test
    void shouldControlEachOperationByExactStateMatrix() {
        log.info("校验逻辑密钥和版本状态矩阵");

        Assertions.assertTrue(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.ACTIVE, KmsOperation.SIGN));
        Assertions.assertTrue(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.ACTIVE, KmsOperation.ENCRYPT));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.RETIRED, KmsOperation.SIGN));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.DISABLED,
                KmsKeyVersionState.ACTIVE, KmsOperation.ENCRYPT));
        Assertions.assertTrue(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.RETIRED, KmsOperation.DECRYPT));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.DISABLED,
                KmsKeyVersionState.RETIRED, KmsOperation.DECRYPT));
        Assertions.assertTrue(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.ACTIVE, KmsOperation.VERIFY));
        Assertions.assertTrue(KmsStateHelper.canExecute(KmsKeyState.DISABLED,
                KmsKeyVersionState.RETIRED, KmsOperation.READ_PUBLIC_KEY));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.PENDING_DESTRUCTION, KmsOperation.VERIFY));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.PENDING_DESTRUCTION,
                KmsKeyVersionState.ACTIVE, KmsOperation.READ_PUBLIC_KEY));
        Assertions.assertFalse(KmsStateHelper.canExecute(null,
                KmsKeyVersionState.ACTIVE, KmsOperation.SIGN));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                null, KmsOperation.SIGN));
        Assertions.assertFalse(KmsStateHelper.canExecute(KmsKeyState.ACTIVE,
                KmsKeyVersionState.ACTIVE, null));
    }

    @Test
    void shouldOnlyAllowDocumentedStateTransitions() {
        log.info("校验密钥及版本状态迁移");

        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyState.ACTIVE,
                KmsKeyState.DISABLED));
        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyState.DISABLED,
                KmsKeyState.PENDING_DESTRUCTION));
        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyState.PENDING_DESTRUCTION,
                KmsKeyState.ACTIVE));
        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyState.PENDING_DESTRUCTION,
                KmsKeyState.DESTROYED));
        Assertions.assertFalse(KmsStateHelper.canTransition(KmsKeyState.ACTIVE,
                KmsKeyState.DESTROYED));
        Assertions.assertFalse(KmsStateHelper.canTransition(KmsKeyState.DESTROYED,
                KmsKeyState.ACTIVE));
        Assertions.assertFalse(KmsStateHelper.canTransition(null, KmsKeyState.ACTIVE));

        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyVersionState.ACTIVE,
                KmsKeyVersionState.RETIRED));
        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyVersionState.RETIRED,
                KmsKeyVersionState.PENDING_DESTRUCTION));
        Assertions.assertTrue(KmsStateHelper.canTransition(KmsKeyVersionState.PENDING_DESTRUCTION,
                KmsKeyVersionState.RETIRED));
        Assertions.assertFalse(KmsStateHelper.canTransition(KmsKeyVersionState.RETIRED,
                KmsKeyVersionState.ACTIVE));
        Assertions.assertFalse(KmsStateHelper.canTransition(KmsKeyVersionState.DESTROYED,
                KmsKeyVersionState.ACTIVE));
        Assertions.assertFalse(KmsStateHelper.canTransition(null, KmsKeyVersionState.ACTIVE));

        Assertions.assertTrue(KmsStateHelper.isPublishablePublicKey(KmsKeyState.DISABLED,
                KmsKeyVersionState.RETIRED));
        Assertions.assertFalse(KmsStateHelper.isPublishablePublicKey(KmsKeyState.ACTIVE,
                KmsKeyVersionState.PENDING_DESTRUCTION));
        Assertions.assertFalse(KmsStateHelper.isPublishablePublicKey(null,
                KmsKeyVersionState.ACTIVE));
    }

    @Test
    void shouldMatchOnlyExactUnexpiredPolicy() {
        log.info("校验 allow-only 策略精确匹配");
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID,
                Collections.<String>emptySet());
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        KmsKeyPolicy policy = KmsKeyPolicy.builder()
                .policyId(POLICY_ID)
                .tenantId(TENANT_ID)
                .keyRef(KEY_REF)
                .principalId(PRINCIPAL_ID)
                .keyVersion(VERSION)
                .operation(KmsOperation.SIGN)
                .expiresAt(now.plusSeconds(SmartKmsCoreConstant.ONE))
                .build();

        Assertions.assertTrue(KmsAuthorizationHelper.matches(policy, principal, KEY_REF,
                VERSION, KmsOperation.SIGN, now));
        Assertions.assertFalse(KmsAuthorizationHelper.matches(policy, principal, KEY_REF,
                VERSION + SmartKmsCoreConstant.ONE, KmsOperation.SIGN, now));
        Assertions.assertFalse(KmsAuthorizationHelper.matches(policy, principal, KEY_REF,
                VERSION, KmsOperation.VERIFY, now));
        Assertions.assertFalse(KmsAuthorizationHelper.matches(policy, principal, "key-ref-b",
                VERSION, KmsOperation.SIGN, now));
        Assertions.assertFalse(KmsAuthorizationHelper.matches(policy, new KmsPrincipal("principal-b",
                TENANT_ID, Collections.<String>emptySet()), KEY_REF, VERSION, KmsOperation.SIGN, now));
        Assertions.assertFalse(KmsAuthorizationHelper.matches(policy, principal, KEY_REF,
                VERSION, KmsOperation.SIGN, now.plusSeconds(SmartKmsCoreConstant.ONE)));
        Assertions.assertFalse(KmsAuthorizationHelper.matches(policy, principal, KEY_REF,
                VERSION, null, now));
    }

    @Test
    void shouldValidateIdentifiersAtBoundary() {
        log.info("校验领域标识输入边界");

        Assertions.assertEquals(TENANT_ID, KmsValidationHelper.requireTenantId(TENANT_ID));
        Assertions.assertThrows(KmsValidationException.class,
                () -> KmsValidationHelper.requireTenantId(null));
        Assertions.assertThrows(KmsValidationException.class,
                () -> KmsValidationHelper.requirePrincipalId(""));
        Assertions.assertThrows(KmsValidationException.class,
                () -> KmsValidationHelper.requireTenantId(" \t"));
        Assertions.assertThrows(KmsValidationException.class,
                () -> KmsValidationHelper.requireKeyRef("key\nref"));
        Assertions.assertThrows(KmsValidationException.class,
                () -> KmsValidationHelper.requirePolicyId(repeat("a",
                        SmartKmsCoreConstant.POLICY_ID_MAX_LENGTH + SmartKmsCoreConstant.ONE)));
        Assertions.assertThrows(KmsValidationException.class,
                () -> KmsValidationHelper.requireRequestId(repeat("a",
                        SmartKmsCoreConstant.REQUEST_ID_MAX_LENGTH + SmartKmsCoreConstant.ONE)));
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = SmartKmsCoreConstant.ZERO; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
