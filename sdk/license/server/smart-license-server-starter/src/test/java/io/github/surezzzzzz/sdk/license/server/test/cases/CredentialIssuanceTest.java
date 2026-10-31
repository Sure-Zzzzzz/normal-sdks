package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.OperationRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

/**
 * 凭据签发服务断言：受理冻结、签名失败进 UNCERTAIN 不盲重发、本地验签拦截错钥、READY 产出可验 JWS。
 *
 * @author surezzzzzz
 */
class CredentialIssuanceTest {

    private static final String HEADER = "eyJhbGciOiJFUzI1NiIsImtpZCI6ImtpZC0xIiwidHlwIjoibGljZW5zZStqd3QifQ";

    private final Map<String, SigningOperationEntity> store = new HashMap<String, SigningOperationEntity>();
    private KeyPair keyPair;

    private KeyPair keyPair() throws Exception {
        if (keyPair == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            keyPair = generator.generateKeyPair();
        }
        return keyPair;
    }

    private CredentialIssuanceService service(LicenseSigner signer, String registeredSpkiHex) {
        SigningProfileRepository profiles = Mockito.mock(SigningProfileRepository.class);
        Mockito.when(profiles.findById("profile-1")).thenReturn(java.util.Optional.of(
                new io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity(
                        "profile-1", "product-1", "CREDENTIAL", "kid-1", "keyref-1", 1,
                        registeredSpkiHex)));
        return new CredentialIssuanceService(new OperationRepository() {
            @Override
            public void save(SigningOperationEntity entity) {
                store.put(entity.getOperationId(), entity);
            }

            @Override
            public SigningOperationEntity find(String operationId) {
                return store.get(operationId);
            }

            @Override
            public void update(SigningOperationEntity entity) {
                store.put(entity.getOperationId(), entity);
            }
        }, signer, profiles);
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) {
            text.append(String.format("%02x", b));
        }
        return text.toString();
    }

    private LicenseSigner workingSigner() throws Exception {
        final KeyPair pair = keyPair();
        return new LicenseSigner() {
            @Override
            public byte[] sign(String signingProfileId, SigningInput input) {
                return io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives
                        .signForTesting(input.getBytes(), pair.getPrivate());
            }
        };
    }

    private LicenseSigner failingSigner() {
        return new LicenseSigner() {
            @Override
            public byte[] sign(String signingProfileId, SigningInput input) {
                throw new RuntimeException("kms timeout");
            }
        };
    }

    private String payloadSegment() {
        // 最小合法 JSON 的 Base64URL（内容不参与本测试断言）
        return io.github.surezzzzzz.sdk.license.core.codec.Base64Url
                .encode("{\"protocolVersion\":2}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void acceptPersistsFrozenInputAtPendingSign() {
        CredentialIssuanceService service = service(failingSigner(), null);
        service.accept("op-1", "cred-1", "grant-1", "profile-1", HEADER, payloadSegment(), "digest-1");
        SigningOperationEntity entity = store.get("op-1");
        assertThat(entity.getState()).isEqualTo(OperationState.PENDING_SIGN.getCode());
        assertThat(entity.getFrozenHeader()).isEqualTo(HEADER);
        assertThat(entity.getExecutionGeneration()).isEqualTo(1L);
    }

    @Test
    void signFailureMarksUncertainAndDoesNotBlindRetry() {
        CredentialIssuanceService service = service(failingSigner(), null);
        service.accept("op-2", "cred-2", "grant-1", "profile-1", HEADER, payloadSegment(), "digest-2");
        assertThatThrownBy(() -> service.signAndCommit("op-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNCERTAIN");
        SigningOperationEntity entity = store.get("op-2");
        assertThat(entity.getState()).isEqualTo(OperationState.UNCERTAIN.getCode());
        assertThat(entity.getReasonCategory()).isEqualTo("SIGN_CALL_FAILED");
    }

    @Test
    void happyPathProducesLocallyVerifiableJws() throws Exception {
        CredentialIssuanceService service = service(workingSigner(), hex(keyPair().getPublic().getEncoded()));
        service.accept("op-3", "cred-3", "grant-1", "profile-1", HEADER, payloadSegment(), "digest-3");
        String jws = service.signAndCommit("op-3");
        io.github.surezzzzzz.sdk.license.core.codec.CompactJws parsed =
                io.github.surezzzzzz.sdk.license.core.codec.CompactJws.parse(jws);
        assertThat(io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives.verify(
                parsed.signingInput(), parsed.signatureBytes(), keyPair().getPublic())).isTrue();
        assertThat(store.get("op-3").getState()).isEqualTo(OperationState.READY.getCode());
    }

    @Test
    void localVerifyRejectsForeignPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        final KeyPair otherPair = generator.generateKeyPair();
        final byte[] otherSpki = otherPair.getPublic().getEncoded();
        // 签名用 A 钥，档案登记 B 钥：本地验签必须拦截
        final KeyPair signingPair = keyPair();
        LicenseSigner mismatchedSigner = new LicenseSigner() {
            @Override
            public byte[] sign(String signingProfileId, SigningInput input) {
                return io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives
                        .signForTesting(input.getBytes(), signingPair.getPrivate());
            }
        };
        // 档案快照=B 钥（签名用 A 钥）：本地验签必须拦截
        CredentialIssuanceService service = service(mismatchedSigner, hex(otherSpki));
        service.accept("op-4", "cred-4", "grant-1", "profile-1", HEADER, payloadSegment(), "digest-4");
        assertThatThrownBy(() -> service.signAndCommit("op-4"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNCERTAIN");
        assertThat(store.get("op-4").getReasonCategory()).isEqualTo("LOCAL_VERIFY_FAILED");
    }

    @Test
    void exactRawHeaderAndPayloadBudgetsAreAcceptedWithoutSecondBase64Inflation() {
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuanceService service = service((profile, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        }, null);
        String header = jsonSegmentWithRawBytes(SmartLicenseCoreConstant.HEADER_MAX_BYTES);
        String payload = jsonSegmentWithRawBytes(SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES);
        service.accept("op-max", "cred-max", "grant-1", "profile-1", header, payload, "idem-max");
        assertThat(store.get("op-max").getState()).isEqualTo("PENDING_SIGN");
        assertThat(store.get("op-max").getFrozenHeader()).isEqualTo(header);
        assertThat(store.get("op-max").getFrozenPayload()).isEqualTo(payload);
        assertThat(header.length() + payload.length() + 2 + Base64Url.encode(new byte[64]).length())
                .isLessThanOrEqualTo(SmartLicenseCoreConstant.JWS_MAX_BYTES);
        assertThat(calls.get()).isZero();
    }

    @Test
    void oneByteOverEitherRawBudgetIsDurablyRejectedBeforeKms() {
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuanceService service = service((profile, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        }, null);
        assertThatThrownBy(() -> service.accept("op-header-over", "cred-header-over", "grant-1", "profile-1",
                jsonSegmentWithRawBytes(SmartLicenseCoreConstant.HEADER_MAX_BYTES + 1), payloadSegment(), "idem-header-over"))
                .isInstanceOf(LicenseHttpException.class);
        assertThatThrownBy(() -> service.accept("op-payload-over", "cred-payload-over", "grant-1", "profile-1",
                HEADER, jsonSegmentWithRawBytes(SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES + 1), "idem-payload-over"))
                .isInstanceOf(LicenseHttpException.class);
        assertThat(store.get("op-header-over").getState()).isEqualTo("REJECTED");
        assertThat(store.get("op-payload-over").getState()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> service.signAndCommit("op-header-over")).isInstanceOf(LicenseHttpException.class);
        assertThatThrownBy(() -> service.signAndCommit("op-payload-over")).isInstanceOf(LicenseHttpException.class);
        assertThat(calls.get()).isZero();
    }

    @Test
    void illegalOrNonCanonicalFrozenSegmentsNeverPersistOrReachKms() {
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuanceService service = service((profile, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        }, null);
        for (String invalid : new String[] {null, "", "=", "a+b", "a/b", "a b", "A", "Zh"}) {
            assertThatThrownBy(() -> service.accept("op-invalid", "cred-invalid", "grant-1", "profile-1",
                    invalid, payloadSegment(), "idem-invalid"))
                    .isInstanceOf(LicenseHttpException.class)
                    .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
            assertThatThrownBy(() -> service.accept("op-invalid", "cred-invalid", "grant-1", "profile-1",
                    HEADER, invalid, "idem-invalid"))
                    .isInstanceOf(LicenseHttpException.class);
        }
        assertThat(store).isEmpty();
        assertThat(calls.get()).isZero();
    }

    /** 合法JSON对象加尾随空格，精确控制UTF-8原始字节预算而不依赖编码后长度。 */
    private static String jsonSegmentWithRawBytes(int bytes) {
        byte[] value = new byte[bytes];
        java.util.Arrays.fill(value, (byte) ' ');
        value[0] = '{';
        value[1] = '}';
        return Base64Url.encode(value);
    }
}
