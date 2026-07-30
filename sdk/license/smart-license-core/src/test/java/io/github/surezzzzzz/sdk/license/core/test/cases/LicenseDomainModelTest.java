package io.github.surezzzzzz.sdk.license.core.test.cases;

import io.github.surezzzzzz.sdk.license.core.constant.LicenseKeyStatus;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseValidationException;
import io.github.surezzzzzz.sdk.license.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * License 不可变领域模型测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseDomainModelTest {

    private static final String TENANT_ID = "tenant-example";
    private static final String KID = "lic-example-es256-v1";
    private static final String COMPACT_JWS = "eyJhbGciOiJFUzI1NiIsImtpZCI6InRlc3QifQ.eyJqdGkiOiJ0ZXN0In0."
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static LicenseKeyMapping mapping(byte[] publicKey) {
        return LicenseKeyMapping.builder().tenantId(TENANT_ID).kid(KID).kmsKeyRef("kms-signing-key").kmsKeyVersion(1)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).publicKey(publicKey)
                .status(LicenseKeyStatus.ACTIVE).build();
    }

    private static LicenseClaims claims(String issuer, Instant issuedAt, Instant notBefore, Instant expiresAt) {
        return LicenseClaims.builder().jti("lic-example-001").issuer(issuer).audience("product-example")
                .issuedAt(issuedAt).notBefore(notBefore).expiresAt(expiresAt)
                .schemaVersion(SmartLicenseCoreConstant.ONE).tenantId(TENANT_ID).customerId("customer-example")
                .deviceKeyFingerprint("sha256:example")
                .terms(Collections.<LicenseTerm>singletonList(new CapacityTerm("nodes", 10))).build();
    }

    @Test
    void shouldNormalizeTermsAndProtectMappingPublicKey() {
        FeatureSetTerm features = new FeatureSetTerm(Arrays.asList("feature-b", "feature-a", "feature-b"));
        byte[] source = new byte[]{1, 2, 3};
        LicenseKeyMapping mapping = mapping(source);
        source[SmartLicenseCoreConstant.ZERO] = 9;
        byte[] exported = mapping.getPublicKey();
        exported[SmartLicenseCoreConstant.ONE] = 8;

        log.info("验证标准条款规范化和公钥防御性拷贝");
        assertEquals(Arrays.asList("feature-a", "feature-b"), features.getFeatures(), "功能集合必须排序并去重");
        assertThrows(UnsupportedOperationException.class, () -> features.getFeatures().add("feature-c"),
                "功能集合必须不可修改");
        assertArrayEquals(new byte[]{1, 2, 3}, mapping.getPublicKey(), "公钥必须隔离构造参数和返回副本的修改");
        assertFalse(mapping.toString().contains(TENANT_ID), "映射默认字符串化不得包含 tenant 标识");
    }

    @Test
    void shouldEnforceClaimsTimeAndTenantInvariants() {
        Instant issuedAt = Instant.ofEpochSecond(100);
        log.info("验证 v1 Claims 的 tenant 与时间不变量");
        assertThrows(LicenseValidationException.class, () -> claims("other-tenant", issuedAt, issuedAt, null),
                "iss 必须与 tenantId 完全一致");
        assertThrows(LicenseValidationException.class, () -> claims(TENANT_ID, issuedAt.plusSeconds(1), issuedAt, null),
                "iat 晚于 nbf 必须拒绝");
        assertThrows(LicenseValidationException.class,
                () -> claims(TENANT_ID, issuedAt, issuedAt, issuedAt), "限期 License 必须满足 nbf 小于 exp");

        LicenseClaims permanent = claims(TENANT_ID, issuedAt, issuedAt, null);
        assertNull(permanent.getExpiresAt(), "永久 License 必须省略过期时间");
        assertFalse(permanent.toString().contains(TENANT_ID), "Claims 默认字符串化不得包含 tenant 标识");
    }

    @Test
    void shouldRejectInvalidTermAndTextInputs() {
        log.info("验证条款范围、控制字符和空白边界");
        assertThrows(LicenseValidationException.class, () -> new CapacityTerm("nodes", -1),
                "容量上限不得为负数");
        assertThrows(LicenseValidationException.class, () -> new TrialTerm(true, 0), "试用天数必须为正数");
        assertThrows(LicenseValidationException.class, () -> new FeatureSetTerm(Collections.<String>emptyList()),
                "功能集合条款不得为空");
        assertThrows(LicenseValidationException.class,
                () -> new FeatureSetTerm(Collections.<String>singletonList(String.valueOf((char) 0))),
                "功能标识不得含控制字符");
        assertThrows(LicenseValidationException.class, () -> new CapacityTerm("　nodes", 1),
                "字段首尾不得为 Unicode 空白");

        LicenseTerm customTerm = () -> "tenant-custom";
        LicenseClaims customTerms = LicenseClaims.builder().jti("lic-example-custom").issuer(TENANT_ID)
                .audience("product-example").issuedAt(Instant.ofEpochSecond(100))
                .notBefore(Instant.ofEpochSecond(100)).schemaVersion(SmartLicenseCoreConstant.ONE).tenantId(TENANT_ID)
                .customerId("customer-example").deviceKeyFingerprint("sha256:example")
                .terms(Collections.singletonList(customTerm)).build();
        assertEquals("tenant-custom", customTerms.getTerms().get(SmartLicenseCoreConstant.ZERO).getType(),
                "Core 必须保留经校验的自定义条款类型");
        assertThrows(LicenseValidationException.class, () -> LicenseClaims.builder().jti("lic-example-invalid")
                .issuer(TENANT_ID).audience("product-example").issuedAt(Instant.ofEpochSecond(100))
                .notBefore(Instant.ofEpochSecond(100)).schemaVersion(SmartLicenseCoreConstant.ONE).tenantId(TENANT_ID)
                .customerId("customer-example").deviceKeyFingerprint("sha256:example")
                .terms(Collections.singletonList(() -> "")).build(), "条款类型不得为空");
    }

    @Test
    void shouldRejectCommandTenantMismatchAndMalformedCompactJws() {
        LicenseClaims claims = claims(TENANT_ID, Instant.ofEpochSecond(100), Instant.ofEpochSecond(100), null);
        log.info("验证命令 tenant 一致性和 Compact JWS 无 padding 结构");
        assertThrows(LicenseValidationException.class,
                () -> LicenseIssueCommand.builder().tenantId("other-tenant").kid(KID).claims(claims).build(),
                "签发命令 tenant 必须与 Claims 一致");
        assertThrows(LicenseValidationException.class, () -> new IssuedLicense("header.payload.signature=", KID, 1),
                "Compact JWS 不得包含 padding");
        assertThrows(LicenseValidationException.class, () -> new IssuedLicense("header.payload.invalid*signature", KID, 1),
                "Compact JWS 段必须是 Base64URL 字符");

        IssuedLicense issuedLicense = new IssuedLicense(COMPACT_JWS, KID, 1);
        assertEquals(KID, issuedLicense.getKid(), "合法 Compact JWS 必须保留业务 kid");
        assertFalse(issuedLicense.toString().contains(COMPACT_JWS), "默认字符串化不得包含完整 JWS");
    }
}
