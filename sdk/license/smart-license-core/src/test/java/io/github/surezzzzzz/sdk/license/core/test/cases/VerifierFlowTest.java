package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ClockPolicy;
import io.github.surezzzzzz.sdk.license.core.model.CredentialKind;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.DeviceBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.OrganizationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.VerifiedCredential;
import io.github.surezzzzzz.sdk.license.core.spi.ClockProvider;
import io.github.surezzzzzz.sdk.license.core.spi.StateStore;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import io.github.surezzzzzz.sdk.license.core.spi.TrustedSubjectProvider;
import io.github.surezzzzzz.sdk.license.core.verify.DefaultLicenseVerifier;
import io.github.surezzzzzz.sdk.license.core.verify.RevocationStore;
import io.github.surezzzzzz.sdk.license.core.verify.TrustedFacts;
import io.github.surezzzzzz.sdk.license.core.verify.VerificationContext;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifier 核心流程端到端：真钥签发→三段 JWS→验签→绑定核对→吊销/时间→阶段与规则 authorize。
 *
 * @author surezzzzzz
 */
@Slf4j
class VerifierFlowTest {

    private static final long NOW = 1780001000L;

    private KeyPair keyPair;
    private TrustedPublicKey trustedKey;
    private Map<String, TrustedPublicKey> trustMap;
    private long trustVersion;
    private FixedClock clock;
    private Map<String, Boolean> revoked;
    private Long revocationIssuedAt;
    private NoopStateStore state;
    private String subjectProfileVersion;
    private long subjectProofValidUntil;
    private boolean subjectProviderFails;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        keyPair = generator.generateKeyPair();
        trustedKey = TrustedPublicKey.of("issuer-1", "product-1", "kid-1",
                TrustedPublicKey.Purpose.CREDENTIAL, 1L, keyPair.getPublic().getEncoded());
        trustMap = new HashMap<String, TrustedPublicKey>();
        trustMap.put("kid-1", trustedKey);
        trustVersion = 1L;
        clock = new FixedClock(NOW);
        revoked = new HashMap<String, Boolean>();
        revocationIssuedAt = NOW;
        state = new NoopStateStore();
        subjectProfileVersion = "local-v1";
        subjectProofValidUntil = Long.MAX_VALUE;
        subjectProviderFails = false;
    }

    private DefaultLicenseVerifier verifier() {
        return new DefaultLicenseVerifier(new MapTrustStore(), new MapRevocationStore(), clock,
                state, java.util.Collections.singleton("local-v1"), new PassingTrustedSubjectProvider());
    }

    /** 构建一份人员固定资格 OFFLINE 凭据的 Compact JWS（真钥签名）。 */
    private String signedCredential(Map<String, Object> rightsOverride, long expOverride) {
        return signedCredential(rightsOverride, expOverride, "local-v1");
    }

    private String signedCredential(Map<String, Object> rightsOverride, long expOverride,
                                   String offlineProofProfile) {
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1");
        Map<String, Object> rights = rightsOverride != null ? rightsOverride : sampleRights();

        io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload.Builder builder =
                io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload.builder()
                        .credentialKind(CredentialKind.OFFLINE)
                        .jti("cred-1").issuerId("issuer-1").productId("product-1")
                        .customerId("customer-1").grantId("grant-1")
                        .grantRevision(1L).grantStateVersion(1L)
                        .eligibilityPolicy(EligibilityPolicy.assignedSubjects(offlineProofProfile))
                        .subjectBinding(io.github.surezzzzzz.sdk.license.core.model.SubjectBinding
                                .personService(ref, "assignment-1", 1L))
                        .issuedAt(1780000000L).notBefore(1780000000L)
                        .validity(ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null))
                        .clockPolicy(ClockPolicy.of(120, 120))
                        .deviceBinding(DeviceBinding.none())
                        .organizationPolicy(OrganizationPolicy.notRequired())
                        .revocationPolicy(RevocationPolicy.optional())
                        .rights(rights)
                        .modelSnapshot(sampleModelSnapshot())
                        .requiredCapabilities(Arrays.asList("license-condition/1", "clock-state/1",
                                "subject-binding/1", "subject-proof/" + offlineProofProfile));
        if (expOverride > 0) {
            builder.expiresAt(expOverride);
        } else {
            builder.expiresAt(1782000000L);
        }
        io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload payload = builder.build();

        String payloadJson = samplePayloadJson(payload, rights, offlineProofProfile);
        String headerJson = "{\"alg\":\"ES256\",\"kid\":\"kid-1\",\"typ\":\"license+jwt\"}";
        String headerSegment = Base64Url.encode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadSegment = Base64Url.encode(payloadJson.getBytes(StandardCharsets.UTF_8));
        byte[] signingInput = (headerSegment + "." + payloadSegment)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] signature = Es256Primitives.signForTesting(signingInput, keyPair.getPrivate());
        return CompactJws.join(headerSegment, payloadSegment, Base64Url.encode(signature));
    }

    private Map<String, Object> sampleRights() {
        Map<String, Object> rights = new LinkedHashMap<String, Object>();
        rights.put("edition", "PRO");
        return rights;
    }

    private Map<String, Object> sampleModelSnapshot() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        Map<String, Object> rightsDefs = new LinkedHashMap<String, Object>();
        Map<String, Object> edition = new LinkedHashMap<String, Object>();
        edition.put("type", "STRING");
        edition.put("required", true);
        edition.put("maxLength", 32);
        rightsDefs.put("edition", edition);
        Map<String, Object> factsDefs = new LinkedHashMap<String, Object>();
        fields.put("rights", rightsDefs);
        fields.put("facts", factsDefs);

        Map<String, Object> actions = new LinkedHashMap<String, Object>();
        Map<String, Object> export = new LinkedHashMap<String, Object>();
        Map<String, Object> compare = new LinkedHashMap<String, Object>();
        compare.put("operator", "EQ");
        compare.put("left", refMap("rights", "edition"));
        compare.put("right", literalMap("PRO"));
        export.put("compare", compare);
        actions.put("data.export", export);

        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("protocol", "license-condition");
        snapshot.put("version", 1);
        snapshot.put("fields", fields);
        snapshot.put("actions", actions);
        return snapshot;
    }

    private Map<String, Object> literalMap(Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("literal", value);
        return map;
    }

    private Map<String, Object> refMap(String namespace, String field) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("ref", namespace + "." + field);
        return map;
    }

    /** 直接以 JSON 拼装 payload（保持与协议文档形态一致）。 */
    private String samplePayloadJson(io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload payload,
                                     Map<String, Object> rights, String offlineProofProfile) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("protocolVersion", 2);
        json.put("credentialKind", "OFFLINE");
        json.put("jti", payload.getJti());
        json.put("iss", payload.getIssuerId());
        json.put("aud", payload.getProductId());
        json.put("customerId", payload.getCustomerId());
        json.put("grantId", payload.getGrantId());
        json.put("grantRevision", payload.getGrantRevision());
        json.put("grantStateVersion", payload.getGrantStateVersion());
        Map<String, Object> eligibility = new LinkedHashMap<String, Object>();
        eligibility.put("kind", "ASSIGNED_SUBJECTS");
        eligibility.put("offlineProofProfile", offlineProofProfile);
        json.put("eligibilityPolicy", eligibility);
        Map<String, Object> binding = new LinkedHashMap<String, Object>();
        binding.put("kind", "PERSON");
        Map<String, Object> subjectRef = new LinkedHashMap<String, Object>();
        subjectRef.put("kind", "PERSON");
        subjectRef.put("sourceId", "identity-source");
        subjectRef.put("subjectId", "person-1");
        binding.put("subjectRef", subjectRef);
        binding.put("assignmentId", "assignment-1");
        binding.put("assignmentGeneration", 1L);
        json.put("subjectBinding", binding);
        json.put("iat", payload.getIssuedAt());
        json.put("nbf", payload.getNotBefore());
        json.put("exp", payload.getExpiresAt());
        Map<String, Object> validity = new LinkedHashMap<String, Object>();
        validity.put("kind", "FIXED");
        validity.put("startsAt", 1780000000L);
        validity.put("serviceUntil", 1781000000L);
        validity.put("commercialGraceUntil", 1782000000L);
        validity.put("graceActions", java.util.Collections.<String>emptyList());
        json.put("validity", validity);
        Map<String, Object> clockPolicy = new LinkedHashMap<String, Object>();
        clockPolicy.put("skewSeconds", 120);
        clockPolicy.put("rollbackToleranceSeconds", 120);
        json.put("clockPolicy", clockPolicy);
        Map<String, Object> device = new LinkedHashMap<String, Object>();
        device.put("mode", "NONE");
        json.put("deviceBinding", device);
        Map<String, Object> org = new LinkedHashMap<String, Object>();
        org.put("required", false);
        json.put("organizationPolicy", org);
        Map<String, Object> revocation = new LinkedHashMap<String, Object>();
        revocation.put("mode", "OPTIONAL");
        json.put("revocationPolicy", revocation);
        json.put("model", sampleModelSnapshot());
        json.put("rights", rights);
        json.put("requiredCapabilities", Arrays.asList("license-condition/1", "clock-state/1",
                "subject-binding/1", "subject-proof/" + offlineProofProfile));
        return StrictJson.write(json);
    }

    private VerificationContext personContext() {
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1");
        return VerificationContext.of("issuer-1", "product-1", "customer-1", "grant-1",
                io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.personService(
                        ref, "assignment-1", 1L));
    }

    @Test
    void fullFlowVerifyThenAuthorizeAllow() {
        String jws = signedCredential(null, 0);
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(
                jws.getBytes(StandardCharsets.UTF_8), personContext());
        assertThat(handle.getJti()).isEqualTo("cred-1");
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(),
                TrustedFacts.none());
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getCommercialPhase()).isEqualTo(Decision.CommercialPhase.ACTIVE);
        assertThat(decision.getConnectivityPhase()).isEqualTo(Decision.ConnectivityPhase.NOT_APPLICABLE);
        assertThat(decision.getRecheckAt()).isEqualTo(1781000000L);
    }

    @Test
    void unregisteredOfflineProofProfileFailsClosed() {
        DefaultLicenseVerifier verifier = verifier();
        assertThatThrownBy(() -> verifier.verify(
                signedCredential(null, 0, "unregistered-v1").getBytes(StandardCharsets.UTF_8),
                personContext()))
                .isInstanceOf(LicenseCoreException.class)
                .extracting(failure -> ((LicenseCoreException) failure).getErrorCode())
                .isEqualTo(io.github.surezzzzzz.sdk.license.core.constant.ErrorCode.CAPABILITY_UNSUPPORTED);
    }

    @Test
    void booleanOnlySubjectEvidenceCannotAuthorizeOfflinePerson() {
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(
                signedCredential(null, 0).getBytes(StandardCharsets.UTF_8), personContext());
        Decision decision = verifier.authorize(handle, "data.export", new BooleanOnlyInputs(),
                TrustedFacts.none());
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void trustedSubjectProfileMismatchFailsClosed() {
        subjectProfileVersion = "local-v0";
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(
                signedCredential(null, 0).getBytes(StandardCharsets.UTF_8), personContext());
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none());
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void expiredTrustedSubjectProofFailsClosed() {
        subjectProofValidUntil = NOW;
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(
                signedCredential(null, 0).getBytes(StandardCharsets.UTF_8), personContext());
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none());
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void trustedSubjectProviderFailureFailsClosed() {
        subjectProviderFails = true;
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(
                signedCredential(null, 0).getBytes(StandardCharsets.UTF_8), personContext());
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none());
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void ruleDenyWhenEditionNotPro() {
        Map<String, Object> basic = new LinkedHashMap<String, Object>();
        basic.put("edition", "BASIC");
        String jws = signedCredential(basic, 0);
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(jws.getBytes(StandardCharsets.UTF_8), personContext());
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(),
                TrustedFacts.none());
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.RULE_DENIED);
    }

    @Test
    void tamperedSignatureRejectedBeforeRules() {
        String jws = signedCredential(null, 0);
        CompactJws parsed = CompactJws.parse(jws);
        byte[] badSignature = new byte[64];
        String tampered = CompactJws.join(parsed.getHeaderSegment(), parsed.getPayloadSegment(),
                Base64Url.encode(badSignature));
        assertThatThrownBy(() -> verifier().verify(tampered.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void revokedCredentialRejected() {
        String jws = signedCredential(null, 0);
        revoked.put("CREDENTIAL:cred-1", true);
        assertThatThrownBy(() -> verifier().verify(jws.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void wrongTrustTargetRejected() {
        String jws = signedCredential(null, 0);
        SubjectRef otherRef = SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-OTHER");
        VerificationContext wrong = VerificationContext.of("issuer-1", "product-1", "customer-1", "grant-1",
                io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.personService(
                        otherRef, "assignment-1", 1L));
        assertThatThrownBy(() -> verifier().verify(jws.getBytes(StandardCharsets.UTF_8), wrong))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void expiredOrNotYetValidRejected() {
        clock.now = 1782000000L; // 恰到 exp（左闭右开：>=exp 拒绝）
        String jws = signedCredential(null, 0);
        assertThatThrownBy(() -> verifier().verify(jws.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class);
        clock.now = 1780000000L - 1L; // 早于 nbf
        assertThatThrownBy(() -> verifier().verify(jws.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void gracePhaseRestrictsActions() {
        clock.now = 1781500000L; // 宽限期（serviceUntil 与宽限截止之间）
        String jws = signedCredential(null, 0);
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(jws.getBytes(StandardCharsets.UTF_8), personContext());
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(),
                TrustedFacts.none());
        assertThat(decision.getCommercialPhase()).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.COMMERCIAL_PHASE);
    }

    @Test
    void authorizeRejectsExpiredHandleAndClockRollback() {
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8), personContext());
        clock.now = 1782000000L;
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
        clock.now = 1779999999L;
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
        clock.now = NOW;
        clock.suspect = true;
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void authorizeRequiresCurrentSubjectDeviceEvidenceAndFacts() {
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8), personContext());
        assertThat(verifier.authorize(handle, "data.export", null, TrustedFacts.none()).getReasonCategory())
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(verifier.authorize(handle, "data.export", () -> false, TrustedFacts.none()).isAllowed()).isFalse();
        assertThat(verifier.authorize(handle, "data.export", new PassingInputs(), null).getReasonCategory())
                .isEqualTo(Decision.ReasonCategory.FACT_MISSING);
    }

    @Test
    void authorizeRechecksCurrentTrustAndSigningKeyRevocation() {
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8), personContext());
        trustMap.clear();
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
        trustMap.put("kid-1", trustedKey);
        revoked.put("SIGNING_KEY:kid-1", true);
        assertThat(verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none())
                .getReasonCategory()).isEqualTo(Decision.ReasonCategory.REVOKED);
        assertThatThrownBy(() -> verifier.verify(signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8), personContext())).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void factoryCannotTurnUnverifiedHandleIntoAuthorization() {
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential real = verifier.verify(signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8), personContext());
        VerifiedCredential forged = VerifiedCredential.create(real.getPayload(), signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> forged.revalidate(verifier))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> verifier.authorize(forged, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void authorizeRetainsInstalledTrustVersionFloorFromInitialVerify() {
        DefaultLicenseVerifier verifier = verifier();
        trustVersion = 2L;
        trustMap.put("kid-1", TrustedPublicKey.of("issuer-1", "product-1", "kid-1",
                TrustedPublicKey.Purpose.CREDENTIAL, 2L, keyPair.getPublic().getEncoded()));
        VerificationContext context = VerificationContext.of("issuer-1", "product-1", "customer-1", "grant-1",
                io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.personService(
                        SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1"),
                        "assignment-1", 1L), 2L);
        VerifiedCredential handle = verifier.verify(signedCredential(null, 0)
                .getBytes(StandardCharsets.UTF_8), context);
        trustMap.put("kid-1", trustedKey);
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void restartCannotForgetTrustedTimeWatermarkOrExtendGraceWithSmallRollback() {
        DefaultLicenseVerifier verifier = verifier();
        String signed = signedCredential(null, 0);
        VerifiedCredential handle = verifier.verify(signed.getBytes(StandardCharsets.UTF_8), personContext());
        clock.now = NOW - 121;
        assertThatThrownBy(() -> verifier().verify(signed.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class);
        state.maximum = 1781000000L;
        clock.now = 1781000000L - 30;
        assertThat(verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none())
                .getCommercialPhase()).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
    }

    @Test
    void authorizeRechecksRequiredRevocationFreshnessAndEarliestDeadline() throws Exception {
        String signed = signedCredential(null, 0);
        CompactJws original = CompactJws.parse(signed);
        com.fasterxml.jackson.databind.node.ObjectNode payload = (com.fasterxml.jackson.databind.node.ObjectNode)
                StrictJson.mapper().readTree(Base64Url.decode(original.getPayloadSegment()));
        com.fasterxml.jackson.databind.node.ObjectNode policy = payload.putObject("revocationPolicy");
        policy.put("mode", "REQUIRED");
        policy.put("maxAgeSeconds", 60);
        payload.withArray("requiredCapabilities").add("revocation-batch/1");
        String segment = Base64Url.encode(StrictJson.write(payload).getBytes(StandardCharsets.UTF_8));
        byte[] signature = Es256Primitives.signForTesting((original.getHeaderSegment() + "." + segment)
                .getBytes(StandardCharsets.US_ASCII), keyPair.getPrivate());
        String credential = CompactJws.join(original.getHeaderSegment(), segment, Base64Url.encode(signature));
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(credential.getBytes(StandardCharsets.UTF_8), personContext());
        assertThat(verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()).getRecheckAt())
                .isEqualTo(NOW + 60);
        clock.now = NOW + 60;
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
        revocationIssuedAt = null;
        assertThatThrownBy(() -> verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void unknownRequiredCapabilityNeverReachesRuleAllow() throws Exception {
        String credential = signedMutation(payload -> payload.withArray("requiredCapabilities").add("future/unknown"));
        log.info("验证未知必需能力的真实签名文件在规则求值前拒绝");
        assertThatThrownBy(() -> verifier().verify(credential.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class)
                .hasMessage(io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage.CAPABILITY_UNSUPPORTED);
    }

    @Test
    void maintenanceWithoutTypedActionPolicyFailsClosed() throws Exception {
        String credential = signedMutation(payload ->
                ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("validity"))
                        .put("maintenanceUntil", 1780000500L));
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(credential.getBytes(StandardCharsets.UTF_8), personContext());
        log.info("验证维护动作政策尚未接线时明确拒绝，不允许到期后规则true无限放行");
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none());
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.CAPABILITY_MISSING);
    }

    @Test
    void trueRuleCannotReplaceMissingNamedSeat() throws Exception {
        String credential = signedMutation(payload -> {
            addQuota(payload, "NAMED_SEAT", "ONLINE");
            payload.withArray("requiredCapabilities").add("named-seat/1");
        });
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(credential.getBytes(StandardCharsets.UTF_8), personContext());
        log.info("验证规则满足但没有实名引用时拒绝，不能凭权益限额猜造实名分配");
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none());
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void matchingRealNamedSeatCanAuthorizeAndWrongAssignmentCannot() throws Exception {
        String credential = signedMutation(payload -> {
            addQuota(payload, "NAMED_SEAT", "ONLINE");
            payload.withArray("requiredCapabilities").add("named-seat/1");
            com.fasterxml.jackson.databind.node.ObjectNode seat = payload.withArray("namedSeatReferences").addObject();
            seat.put("assignmentId", "assignment-1");
            seat.put("assignmentGeneration", 1);
            seat.put("metric", "capacity");
            seat.put("quantity", 1);
            seat.put("retention", "FIXED");
            seat.put("retainUntil", 1782000000L);
        });
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(credential.getBytes(StandardCharsets.UTF_8), personContext());
        log.info("验证真实实名引用与凭据固定资格同编号同代数才可使用");
        assertThat(verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none()).isAllowed())
                .isTrue();
        String wrongAssignment = signedMutation(payload -> {
            addQuota(payload, "NAMED_SEAT", "ONLINE");
            payload.withArray("requiredCapabilities").add("named-seat/1");
            com.fasterxml.jackson.databind.node.ObjectNode seat = payload.withArray("namedSeatReferences").addObject();
            seat.put("assignmentId", "another-assignment");
            seat.put("assignmentGeneration", 1);
            seat.put("metric", "capacity");
            seat.put("quantity", 1);
            seat.put("retention", "PERPETUAL");
        });
        assertThatThrownBy(() -> verifier.verify(wrongAssignment.getBytes(StandardCharsets.UTF_8), personContext()))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void trueRuleCannotReplaceAtomicLocalQuotaLedger() throws Exception {
        String credential = signedMutation(payload -> {
            addQuota(payload, "CONSUMPTION", "LOCAL");
            payload.withArray("requiredCapabilities").add("quota-local/1");
            com.fasterxml.jackson.databind.node.ArrayNode capabilities = payload.withArray("requiredCapabilities");
            for (int i = capabilities.size() - 1; i >= 0; i--) {
                if ("subject-proof/local-v1".equals(capabilities.get(i).asText())) {
                    capabilities.remove(i);
                }
            }
            payload.putObject("eligibilityPolicy").put("kind", "ALLOCATED_DEPLOYMENTS");
            com.fasterxml.jackson.databind.node.ObjectNode binding = payload.putObject("subjectBinding");
            binding.put("kind", "DEPLOYMENT");
            binding.put("activationId", "activation-1");
            binding.put("activationGeneration", 1);
            com.fasterxml.jackson.databind.node.ObjectNode allocation = payload.withArray("quotaAllocations").addObject();
            allocation.put("allocationId", "allocation-1");
            allocation.put("metric", "capacity");
            allocation.put("quantity", 10);
            allocation.put("grantRevision", 1);
            allocation.put("retention", "FIXED");
            allocation.put("retainUntil", 1782000000L);
        });
        DefaultLicenseVerifier verifier = verifier();
        VerificationContext context = VerificationContext.of("issuer-1", "product-1", "customer-1", "grant-1",
                io.github.surezzzzzz.sdk.license.core.model.SubjectBinding.deployment("activation-1", 1));
        VerifiedCredential handle = verifier.verify(credential.getBytes(StandardCharsets.UTF_8), context);
        log.info("验证本地分配仅为上限，无原子台账事务入口时明确拒绝额度动作");
        Decision decision = verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none());
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.CAPABILITY_MISSING);
    }

    @Test
    void onlineConsumptionCannotBeUsedThroughOfflineRuleAllow() throws Exception {
        String credential = signedMutation(payload -> addQuota(payload, "CONSUMPTION", "ONLINE"));
        DefaultLicenseVerifier verifier = verifier();
        VerifiedCredential handle = verifier.verify(credential.getBytes(StandardCharsets.UTF_8), personContext());
        log.info("验证在线全局累计扣减不能由本地规则允许结果代替");
        assertThat(verifier.authorize(handle, "data.export", new PassingInputs(), TrustedFacts.none())
                .getReasonCategory()).isEqualTo(Decision.ReasonCategory.CAPABILITY_MISSING);
    }

    /** 在真实验签夹具基础上重签修改后的载荷，确保断言针对业务守卫而非签名失败。 */
    private String signedMutation(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> mutation)
            throws Exception {
        CompactJws original = CompactJws.parse(signedCredential(null, 0));
        com.fasterxml.jackson.databind.node.ObjectNode payload = (com.fasterxml.jackson.databind.node.ObjectNode)
                StrictJson.mapper().readTree(Base64Url.decode(original.getPayloadSegment()));
        mutation.accept(payload);
        String segment = Base64Url.encode(StrictJson.write(payload).getBytes(StandardCharsets.UTF_8));
        byte[] signature = Es256Primitives.signForTesting((original.getHeaderSegment() + "." + segment)
                .getBytes(StandardCharsets.US_ASCII), keyPair.getPrivate());
        return CompactJws.join(original.getHeaderSegment(), segment, Base64Url.encode(signature));
    }

    /** 增加完整额度政策与权益字段；不替换原规则，使额度拒绝与规则拒绝可明确区分。 */
    private void addQuota(com.fasterxml.jackson.databind.node.ObjectNode payload, String kind, String enforcement) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("rights")).put("capacity", 10);
        com.fasterxml.jackson.databind.node.ObjectNode field =
                ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("model").path("fields").path("rights"))
                        .putObject("capacity");
        field.put("type", "INTEGER");
        field.put("minimum", 0);
        field.put("maximum", 100);
        com.fasterxml.jackson.databind.node.ObjectNode quota = payload.putObject("quotaPolicy");
        com.fasterxml.jackson.databind.node.ObjectNode definition = quota.putObject("definitions").putObject("capacity");
        definition.put("limitRight", "capacity");
        definition.put("kind", kind);
        definition.put("enforcement", enforcement);
        if ("CONSUMPTION".equals(kind)) {
            definition.put("window", "TOTAL");
        }
        quota.putObject("constraints").putArray("data.export").add("capacity");
        quota.putObject("windowAnchors");
    }

    private static final class PassingInputs implements DefaultLicenseVerifier.FileVerificationInputs {
        @Override
        public boolean isSubjectAndDeviceAcceptable() {
            return true;
        }

        @Override
        public Optional<SubjectRef> authenticatedSubject(String offlineProofProfile) {
            return Optional.of(SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1"));
        }
    }

    private static final class BooleanOnlyInputs implements DefaultLicenseVerifier.FileVerificationInputs {
        @Override
        public boolean isSubjectAndDeviceAcceptable() {
            return true;
        }
    }

    private final class PassingTrustedSubjectProvider implements TrustedSubjectProvider {
        @Override
        public Optional<SubjectRef> currentTrustedSubject(String offlineProofProfile) {
            if (subjectProviderFails) {
                throw new IllegalStateException("identity unavailable");
            }
            return Optional.of(SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1"));
        }

        @Override
        public String profileVersion() {
            return subjectProfileVersion;
        }

        @Override
        public long proofValidUntil() {
            return subjectProofValidUntil;
        }
    }

    private final class MapTrustStore implements TrustStore {
        @Override
        public Optional<TrustedPublicKey> findKey(String issuerId, String productId, String kid,
                                                  TrustedPublicKey.Purpose purpose) {
            TrustedPublicKey key = trustMap.get(kid);
            if (key == null || !key.getIssuerId().equals(issuerId)
                    || !key.getProductId().equals(productId) || key.getPurpose() != purpose) {
                return Optional.empty();
            }
            return Optional.of(key);
        }

        @Override
        public void install(TrustedPublicKey key) {
            trustMap.put(key.getKid(), key);
        }

        @Override
        public long currentTrustVersion(String issuerId, String productId, TrustedPublicKey.Purpose purpose) {
            return trustVersion;
        }
    }

    private final class MapRevocationStore implements RevocationStore {
        @Override
        public boolean isRevoked(String targetKind, String targetId) {
            return Boolean.TRUE.equals(revoked.get(targetKind + ":" + targetId));
        }

        @Override
        public OptionalLong installedSequence(String issuerId, String productId) {
            return OptionalLong.of(1L);
        }

        @Override
        public Optional<Long> installedIssuedAt(String issuerId, String productId) {
            return Optional.ofNullable(revocationIssuedAt);
        }
    }

    private static final class NoopStateStore implements StateStore {
        long maximum = Long.MIN_VALUE;
        @Override
        public void persistMaxTrustedTime(String stateKey, long epochSeconds) {
            maximum = Math.max(maximum, epochSeconds);
        }

        @Override
        public OptionalLong loadMaxTrustedTime(String stateKey) {
            return maximum == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(maximum);
        }

        @Override
        public void persistRevocationSequence(String issuerId, String productId, long sequence) {
        }

        @Override
        public OptionalLong loadRevocationSequence(String issuerId, String productId) {
            return OptionalLong.empty();
        }

        @Override
        public void persistNegativeWatermark(String stateKey, String scope, long version) {
        }

        @Override
        public OptionalLong loadNegativeWatermark(String stateKey, String scope) {
            return OptionalLong.empty();
        }

        @Override
        public void persistRecoveryGeneration(String stateKey, long generation) {
        }

        @Override
        public OptionalLong loadRecoveryGeneration(String stateKey) {
            return OptionalLong.empty();
        }

        @Override
        public String buildStateKey(String issuerId, String productId, String customerId, String grantId,
                                    String stableObjectKey) {
            return issuerId + ":" + productId + ":" + customerId + ":" + grantId + ":" + stableObjectKey;
        }
    }

    private static final class FixedClock implements ClockProvider {
        long now;
        boolean suspect;

        FixedClock(long now) {
            this.now = now;
        }

        @Override
        public long currentEffectiveNow() {
            return now;
        }

        @Override
        public boolean isClockSuspect() {
            return suspect;
        }

        @Override
        public long monotonicMillis() {
            return 0L;
        }
    }
}
