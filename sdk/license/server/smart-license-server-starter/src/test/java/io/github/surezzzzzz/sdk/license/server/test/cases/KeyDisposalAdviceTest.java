package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseSigningProfileController;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.SigningProfileRegistrationService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * 退役档案钥销毁保护验收：保留窗口内引用计数与 RETAIN/SAFE 判定、在用档案不参与销毁治理、
 * keyRef 日志脱敏指纹（全文不出现在任何输出面）。
 *
 * @author surezzzzzz
 */
class KeyDisposalAdviceTest {

    private final SigningProfileRepository profiles = mock(SigningProfileRepository.class);
    private final SigningOperationRepository operations = mock(SigningOperationRepository.class);
    private final SigningProfileRegistrationService registration =
            mock(SigningProfileRegistrationService.class);
    private final LicenseSigningProfileController controller =
            new LicenseSigningProfileController(profiles, operations, registration);

    /** 两种登记入口必须在读库或访问签名服务之前使用相同的 DATA 治理门禁。 */
    @Test
    void registrationEntrypointsRejectMissingDataBeforeAnySideEffect() {
        assertThatThrownBy(() -> controller.create("{}",
                LicenseIdentityHttpTest.TestRequests.noDataGrant()))
                .isInstanceOf(LicenseHttpException.class)
                .satisfies(failure -> assertThat(((LicenseHttpException) failure).getStatus()).isEqualTo(403));
        assertThatThrownBy(() -> controller.selectForPurpose("prod-1", "CREDENTIAL", "{}",
                LicenseIdentityHttpTest.TestRequests.noDataGrant()))
                .isInstanceOf(LicenseHttpException.class)
                .satisfies(failure -> assertThat(((LicenseHttpException) failure).getStatus()).isEqualTo(403));
        verifyNoInteractions(profiles, operations, registration);
    }

    /** 退役档案 + 一条 READY 任务（载荷 exp 相对当前偏移）。 */
    private void staged(String profileId, long expOffsetSeconds, long graceOffsetSeconds) {
        SigningProfileEntity entity = new SigningProfileEntity(profileId, "prod-1", "CREDENTIAL",
                "kid-1", "keyref-secret-value", 1, repeat(91));
        entity.retire();
        when(profiles.findById(profileId)).thenReturn(Optional.of(entity));
        if (expOffsetSeconds == Long.MIN_VALUE && graceOffsetSeconds == Long.MIN_VALUE) {
            when(operations.findBySigningProfileIdAndState(profileId, "READY"))
                    .thenReturn(Collections.emptyList());
            return;
        }
        long now = Instant.now().getEpochSecond();
        String payloadJson = "{\"exp\":" + (now + expOffsetSeconds)
                + ",\"validity\":{\"serviceUntil\":" + (now + expOffsetSeconds)
                + ",\"commercialGraceUntil\":" + (now + graceOffsetSeconds) + "}}";
        SigningOperationEntity task = new SigningOperationEntity("op-x", "cred-x", "grant-x",
                profileId, "h", Base64Url.encode(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "digest");
        task.setState("READY");
        when(operations.findBySigningProfileIdAndState(profileId, "READY"))
                .thenReturn(Collections.singletonList(task));
    }

    @Test
    void liveCredentialReferenceRetainsKey() {
        String profileId = "sp-live";
        staged(profileId, 3600L, 7200L);
        ResponseEntity<String> response = controller.disposalAdvice(profileId,
                TestPlans.allFor("license-signing-profile", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(response.getBody()).contains("\"advice\":\"RETAIN_UNTIL_WINDOW_PASSED\"");
        assertThat(response.getBody()).contains("\"referencingLiveCredentials\":1");
        // 宽限期晚于 exp：保留窗按最大值
        assertThat(response.getBody()).contains("\"retainUntilEpoch\"");
    }

    @Test
    void expiredReferencesSafeToScheduleDestruction() {
        String profileId = "sp-expired";
        staged(profileId, -3600L, -1800L);
        ResponseEntity<String> response = controller.disposalAdvice(profileId,
                TestPlans.allFor("license-signing-profile", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(response.getBody()).contains("\"advice\":\"SAFE_TO_SCHEDULE_DESTRUCTION\"");
        assertThat(response.getBody()).contains("\"referencingLiveCredentials\":0");
    }

    @Test
    void noReferencesSafeToScheduleDestruction() {
        String profileId = "sp-unused";
        staged(profileId, Long.MIN_VALUE, Long.MIN_VALUE);
        ResponseEntity<String> response = controller.disposalAdvice(profileId,
                TestPlans.allFor("license-signing-profile", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(response.getBody()).contains("\"advice\":\"SAFE_TO_SCHEDULE_DESTRUCTION\"");
    }

    /** 在用档案不参与销毁治理（销毁保护只对退役档案开放，防误删在用钥材料）。 */
    @Test
    void activeProfileRejectedForDisposalAdvice() {
        SigningProfileEntity active = new SigningProfileEntity("sp-active", "prod-1", "CREDENTIAL",
                "kid-1", "keyref-secret-value", 1, repeat(91));
        when(profiles.findById("sp-active")).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> controller.disposalAdvice("sp-active",
                TestPlans.allFor("license-signing-profile", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    /** 永久凭据省略 exp 时，钥材料仍需无截止保留，不能误报可销毁。 */
    @Test
    void perpetualCredentialRetainsKeyWithoutInventedDeadline() {
        assertIndefiniteRetention("{\"validity\":{\"kind\":\"PERPETUAL\",\"startsAt\":1000}}");
    }

    /** 坏载荷或无法解释的截止失败关闭，并且响应不能因日期转换溢出而返回 500。 */
    @Test
    void malformedOrUnboundedPayloadRetainsKeyWithoutDateOverflow() {
        for (String payload : new String[] {"not-json", "{}", "{\"exp\":\"invalid\"}",
                "{\"exp\":9223372036854775807}", "{\"exp\":null}"}) {
            assertIndefiniteRetention(payload);
        }
    }

    private void assertIndefiniteRetention(String payload) {
        String profileId = "sp-indefinite";
        SigningProfileEntity profile = new SigningProfileEntity(profileId, "prod-1", "CREDENTIAL",
                "kid-1", "keyref-example", 1, repeat(91));
        profile.retire();
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        SigningOperationEntity task = new SigningOperationEntity("op-indefinite", "cred-indefinite", "gr-1",
                profileId, "h", Base64Url.encode(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "digest-indefinite");
        task.setState("READY");
        when(operations.findBySigningProfileIdAndState(profileId, "READY"))
                .thenReturn(Collections.singletonList(task));
        ResponseEntity<String> response = controller.disposalAdvice(profileId,
                TestPlans.allFor("license-signing-profile", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"advice\":\"RETAIN_UNTIL_WINDOW_PASSED\"",
                "\"retainIndefinitely\":true", "\"retainUntilEpoch\":null", "\"retainUntilIso\":null",
                "\"referencingLiveCredentials\":1");
    }

    private static String repeat(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append("ab");
        }
        return text.toString();
    }

    /** keyRef 脱敏：登记服务日志辅助的指纹函数不回全文（响应/日志都不携带钥引用）。 */
    @Test
    void keyRefFingerprintNeverExposesFullValue() {
        String secret = "kms-key-ref-" + UUID.randomUUID();
        String fingerprint = SigningProfileRegistrationService.fingerprintOf(secret);
        assertThat(fingerprint).hasSize(17).endsWith("…");
        assertThat(fingerprint).doesNotContain(secret);
        assertThat(fingerprint).isNotEqualTo(secret);
        assertThat(SigningProfileRegistrationService.fingerprintOf(null)).isEqualTo("none");
        assertThat(SigningProfileRegistrationService.fingerprintOf("")).isEqualTo("none");
    }
}
