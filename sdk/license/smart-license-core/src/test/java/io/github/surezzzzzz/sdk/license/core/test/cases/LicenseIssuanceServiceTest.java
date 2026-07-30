package io.github.surezzzzzz.sdk.license.core.test.cases;

import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult;
import io.github.surezzzzzz.sdk.kms.client.port.TenantPublicKeyPort;
import io.github.surezzzzzz.sdk.kms.client.port.TenantSignerPort;
import io.github.surezzzzzz.sdk.license.core.constant.LicenseKeyStatus;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseIssuanceException;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseKeyMappingException;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseSerializationException;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseValidationException;
import io.github.surezzzzzz.sdk.license.core.model.*;
import io.github.surezzzzzz.sdk.license.core.repository.LicenseKeyMappingRepository;
import io.github.surezzzzzz.sdk.license.core.spi.LicensePayloadCodec;
import io.github.surezzzzzz.sdk.license.core.support.DefaultLicenseIssuanceService;
import io.github.surezzzzzz.sdk.license.core.support.DefaultLicenseKeyMappingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * License 映射与签发服务测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseIssuanceServiceTest {

    private static final String TENANT_ID = "tenant-example";
    private static final String KID = "lic-example-es256-v1";
    private static final String KEY_REF = "kms-signing-key";
    private static final int KEY_VERSION = 1;
    private static final byte[] PUBLIC_KEY = new byte[]{1, 2, 3};
    private static final byte[] JOSE_SIGNATURE = new byte[SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_LENGTH];
    private static final String PAYLOAD = "{\"jti\":\"lic-example-001\"}";

    private static void assertIssuanceRejected(InMemoryRepository repository, KmsSigningResult result, String message) {
        DefaultLicenseIssuanceService service = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(result), new FixedPayloadCodec(PAYLOAD));
        assertThrows(LicenseIssuanceException.class, () -> service.issue(command()), message);
    }

    private static void assertMappingRejected(LicenseKeyMapping mapping, KmsPublicKey publicKey, String message) {
        DefaultLicenseKeyMappingService service = new DefaultLicenseKeyMappingService(new InMemoryRepository(),
                new RecordingPublicKeyPort(publicKey));
        assertThrows(LicenseKeyMappingException.class, () -> service.create(mapping), message);
    }

    private static LicenseKeyMapping mapping(LicenseKeyStatus status) {
        return LicenseKeyMapping.builder().tenantId(TENANT_ID).kid(KID).kmsKeyRef(KEY_REF).kmsKeyVersion(KEY_VERSION)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).publicKey(PUBLIC_KEY).status(status).build();
    }

    private static LicenseIssueCommand command() {
        Instant now = Instant.ofEpochSecond(100);
        return LicenseIssueCommand.builder().tenantId(TENANT_ID).kid(KID).claims(LicenseClaims.builder()
                .jti("lic-example-001").issuer(TENANT_ID).audience("product-example").issuedAt(now)
                .notBefore(now).schemaVersion(SmartLicenseCoreConstant.ONE).tenantId(TENANT_ID)
                .customerId("customer-example").deviceKeyFingerprint("sha256:example")
                .terms(Collections.<LicenseTerm>singletonList(new CapacityTerm("nodes", 10))).build()).build();
    }

    private static KmsPublicKey standardPublicKey() {
        return KmsPublicKey.builder().keyRef(KEY_REF).version(KEY_VERSION)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).state(LicenseKeyStatus.ACTIVE.getCode())
                .publicKey(PUBLIC_KEY).build();
    }

    private static KmsSigningResult standardSigningResult() {
        return KmsSigningResult.builder().version(KEY_VERSION).algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256)
                .signature(JOSE_SIGNATURE).build();
    }

    @Test
    void shouldCreateMappingAndIssueExactCompactJws() {
        InMemoryRepository repository = new InMemoryRepository();
        RecordingPublicKeyPort publicKeyPort = new RecordingPublicKeyPort(standardPublicKey());
        LicenseKeyMapping mapping = mapping(LicenseKeyStatus.ACTIVE);
        LicenseKeyMapping created = new DefaultLicenseKeyMappingService(repository, publicKeyPort).create(mapping);
        RecordingSignerPort signerPort = new RecordingSignerPort(standardSigningResult());
        IssuedLicense issued = new DefaultLicenseIssuanceService(repository, signerPort, new FixedPayloadCodec(PAYLOAD))
                .issue(command());

        String[] segments = issued.getCompactJws().split("\\.", -1);
        log.info("签发结果：KMS版本={}，JWS段数={}", issued.getKmsKeyVersion(), segments.length);
        assertEquals(mapping, created, "创建结果必须保留不可变映射");
        assertEquals(SmartLicenseCoreConstant.JWS_SEGMENT_COUNT, segments.length, "必须生成三段 Compact JWS");
        assertEquals("{\"alg\":\"ES256\",\"kid\":\"" + KID + "\",\"typ\":\"JWT\"}",
                new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8),
                "protected header 必须只按固定顺序包含 alg、kid、typ");
        assertEquals(PAYLOAD, new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8),
                "payload codec 输出必须原样编码，不得被 Core 二次序列化");
        assertFalse(issued.getCompactJws().contains("="), "JWS 不得包含 Base64URL padding");
        assertEquals(KEY_REF, signerPort.keyRef, "必须使用映射中的 KMS keyRef");
        assertEquals(Integer.valueOf(KEY_VERSION), signerPort.version, "必须使用映射中的精确 KMS 版本");
        assertArrayEquals((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII), signerPort.input,
                "KMS 必须接收原始 header.payload ASCII 输入");
        assertEquals(SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_BASE64URL_LENGTH, segments[2].length(),
                "ES256 JOSE 签名段必须是固定无 padding 长度");
        assertNotNull(publicKeyPort.requestedVersion, "创建映射必须显式读取 KMS 版本公钥");
    }

    @Test
    void shouldCreateRetiredMappingForHistoricalVerificationOnly() {
        InMemoryRepository repository = new InMemoryRepository();
        LicenseKeyMapping retiredMapping = mapping(LicenseKeyStatus.RETIRED);
        KmsPublicKey retiredPublicKey = KmsPublicKey.builder().keyRef(KEY_REF).version(KEY_VERSION)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).state(LicenseKeyStatus.RETIRED.getCode())
                .publicKey(PUBLIC_KEY).build();

        log.info("验证 RETIRED 映射可保存历史验签投影但不得新签发");
        assertEquals(retiredMapping, new DefaultLicenseKeyMappingService(repository,
                        new RecordingPublicKeyPort(retiredPublicKey)).create(retiredMapping),
                "KMS RETIRED 公钥必须可创建历史验证映射");
        assertThrows(LicenseIssuanceException.class,
                () -> new DefaultLicenseIssuanceService(repository, new RecordingSignerPort(standardSigningResult()),
                        new FixedPayloadCodec(PAYLOAD)).issue(command()),
                "RETIRED 映射不得用于新签发");
    }

    @Test
    void shouldRejectKmsInconsistencyAndInactiveMapping() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.mapping = mapping(LicenseKeyStatus.ACTIVE);
        DefaultLicenseIssuanceService wrongVersionService = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(KmsSigningResult.builder().version(KEY_VERSION + 1)
                        .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).signature(JOSE_SIGNATURE).build()),
                new FixedPayloadCodec(PAYLOAD));
        log.info("验证 KMS 版本不一致时必须失败关闭");
        assertThrows(LicenseIssuanceException.class, () -> wrongVersionService.issue(command()),
                "KMS 实际版本不一致必须拒绝签发");

        repository.mapping = mapping(LicenseKeyStatus.RETIRED);
        DefaultLicenseIssuanceService retiredService = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(standardSigningResult()), new FixedPayloadCodec(PAYLOAD));
        log.info("验证 RETIRED 映射不得新签发");
        assertThrows(LicenseIssuanceException.class, () -> retiredService.issue(command()),
                "RETIRED 映射不得用于新签发");
    }

    @Test
    void shouldRejectInvalidKmsPublicKeyMappingAndRemapping() {
        InMemoryRepository repository = new InMemoryRepository();
        LicenseKeyMapping mapping = mapping(LicenseKeyStatus.ACTIVE);
        DefaultLicenseKeyMappingService invalidService = new DefaultLicenseKeyMappingService(repository,
                new RecordingPublicKeyPort(KmsPublicKey.builder().keyRef(KEY_REF).version(KEY_VERSION)
                        .algorithm("RS256").state(LicenseKeyStatus.ACTIVE.getCode()).publicKey(PUBLIC_KEY).build()));
        log.info("验证 KMS 算法不一致时不得创建映射");
        assertThrows(LicenseKeyMappingException.class, () -> invalidService.create(mapping),
                "KMS 公钥算法不一致必须拒绝映射");

        repository.mapping = mapping;
        DefaultLicenseKeyMappingService remappingService = new DefaultLicenseKeyMappingService(repository,
                new RecordingPublicKeyPort(standardPublicKey()));
        log.info("验证已存在 kid 不得重绑");
        assertThrows(LicenseKeyMappingException.class, () -> remappingService.create(mapping),
                "已存在 tenant/kid 映射不得重绑");
    }

    @Test
    void shouldRejectEveryInvalidKmsSigningResultAndPortFailure() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.mapping = mapping(LicenseKeyStatus.ACTIVE);
        log.info("验证 KMS 签名结果为空、算法错误、长度错误和端口异常时均失败关闭");
        assertIssuanceRejected(repository, null, "KMS 返回 null 必须拒绝签发");
        assertIssuanceRejected(repository, KmsSigningResult.builder().version(KEY_VERSION)
                .algorithm("RS256").signature(JOSE_SIGNATURE).build(), "KMS 算法不一致必须拒绝签发");
        assertIssuanceRejected(repository, KmsSigningResult.builder().version(KEY_VERSION)
                        .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).signature(new byte[1]).build(),
                "KMS 非 JOSE 64 字节签名必须拒绝");
        assertIssuanceRejected(repository, KmsSigningResult.builder().version(KEY_VERSION)
                        .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).signature(null).build(),
                "KMS 空签名必须拒绝");

        DefaultLicenseIssuanceService portFailureService = new DefaultLicenseIssuanceService(repository,
                new TenantSignerPort() {
                    @Override
                    public KmsSigningResult sign(String keyRef, Integer version, byte[] signingInput) {
                        throw new IllegalStateException("test failure");
                    }
                }, new FixedPayloadCodec(PAYLOAD));
        assertThrows(LicenseIssuanceException.class, () -> portFailureService.issue(command()),
                "KMS 端口异常必须转换为安全签发失败");

        repository.mapping = LicenseKeyMapping.builder().tenantId("other-tenant").kid(KID).kmsKeyRef(KEY_REF)
                .kmsKeyVersion(KEY_VERSION).algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256)
                .publicKey(PUBLIC_KEY).status(LicenseKeyStatus.ACTIVE).build();
        assertThrows(LicenseIssuanceException.class,
                () -> new DefaultLicenseIssuanceService(repository, new RecordingSignerPort(standardSigningResult()),
                        new FixedPayloadCodec(PAYLOAD)).issue(command()),
                "仓储返回其他 tenant 映射必须拒绝签发");
    }

    @Test
    void shouldRejectEveryInvalidKmsPublicKeyAndPortFailure() {
        LicenseKeyMapping mapping = mapping(LicenseKeyStatus.ACTIVE);
        log.info("验证 KMS 公钥 keyRef、版本、状态、公钥和端口异常时均不得创建映射");
        assertMappingRejected(mapping, KmsPublicKey.builder().keyRef("other-key").version(KEY_VERSION)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).state(LicenseKeyStatus.ACTIVE.getCode())
                .publicKey(PUBLIC_KEY).build(), "KMS keyRef 不一致必须拒绝映射");
        assertMappingRejected(mapping, KmsPublicKey.builder().keyRef(KEY_REF).version(KEY_VERSION + 1)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).state(LicenseKeyStatus.ACTIVE.getCode())
                .publicKey(PUBLIC_KEY).build(), "KMS 版本不一致必须拒绝映射");
        assertMappingRejected(mapping, KmsPublicKey.builder().keyRef(KEY_REF).version(KEY_VERSION)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).state(LicenseKeyStatus.RETIRED.getCode())
                .publicKey(PUBLIC_KEY).build(), "KMS 状态不一致必须拒绝映射");
        assertMappingRejected(mapping, KmsPublicKey.builder().keyRef(KEY_REF).version(KEY_VERSION)
                .algorithm(SmartLicenseCoreConstant.ALGORITHM_ES256).state(LicenseKeyStatus.ACTIVE.getCode())
                .publicKey(new byte[]{9}).build(), "KMS 公钥字节不一致必须拒绝映射");

        DefaultLicenseKeyMappingService portFailureService = new DefaultLicenseKeyMappingService(new InMemoryRepository(),
                new TenantPublicKeyPort() {
                    @Override
                    public KmsPublicKey read(String keyRef, Integer version) {
                        throw new IllegalStateException("test failure");
                    }

                    @Override
                    public List<KmsPublicKey> list(String keyRef) {
                        return Collections.emptyList();
                    }
                });
        assertThrows(LicenseKeyMappingException.class, () -> portFailureService.create(mapping),
                "KMS 公钥端口异常必须转换为安全映射失败");
    }

    @Test
    void shouldRejectMissingMappingAndEmptyCodecOutput() {
        InMemoryRepository repository = new InMemoryRepository();
        DefaultLicenseIssuanceService missingMappingService = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(standardSigningResult()), new FixedPayloadCodec(PAYLOAD));
        log.info("验证缺失映射和空 payload codec 输出时不得签发");
        assertThrows(LicenseIssuanceException.class, () -> missingMappingService.issue(command()),
                "缺失业务 kid 映射必须拒绝签发");

        repository.mapping = mapping(LicenseKeyStatus.ACTIVE);
        DefaultLicenseIssuanceService nullPayloadService = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(standardSigningResult()), new FixedPayloadCodec(null));
        assertThrows(LicenseSerializationException.class, () -> nullPayloadService.issue(command()),
                "payload codec 返回 null 必须拒绝签发");
        DefaultLicenseIssuanceService emptyPayloadService = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(standardSigningResult()), new FixedPayloadCodec(""));
        assertThrows(LicenseSerializationException.class, () -> emptyPayloadService.issue(command()),
                "payload codec 返回空文本必须拒绝签发");
    }

    @Test
    void shouldRejectCodecFailureAndInvalidClaimTimeOrder() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.mapping = mapping(LicenseKeyStatus.ACTIVE);
        DefaultLicenseIssuanceService codecFailureService = new DefaultLicenseIssuanceService(repository,
                new RecordingSignerPort(standardSigningResult()), new LicensePayloadCodec() {
            @Override
            public String encodeV1(LicenseClaims claims) {
                throw new IllegalStateException("test failure");
            }
        });
        log.info("验证 payload codec 异常时不得签发");
        assertThrows(LicenseSerializationException.class, () -> codecFailureService.issue(command()),
                "payload codec 失败必须转换为安全签发失败");

        Instant now = Instant.ofEpochSecond(100);
        log.info("验证 iat 晚于 nbf 时必须拒绝 Claims");
        assertThrows(LicenseValidationException.class, () -> LicenseClaims.builder()
                .jti("license-2").issuer(TENANT_ID).audience("product-example").issuedAt(now.plusSeconds(1))
                .notBefore(now).schemaVersion(SmartLicenseCoreConstant.ONE).tenantId(TENANT_ID)
                .customerId("customer-example").deviceKeyFingerprint("sha256:example")
                .terms(Collections.<LicenseTerm>emptyList()).build(), "iat 晚于 nbf 必须被拒绝");
    }

    private static final class InMemoryRepository implements LicenseKeyMappingRepository {
        private LicenseKeyMapping mapping;

        @Override
        public Optional<LicenseKeyMapping> findByTenantIdAndKid(String tenantId, String kid) {
            return mapping == null ? Optional.<LicenseKeyMapping>empty() : Optional.of(mapping);
        }

        @Override
        public void create(LicenseKeyMapping value) {
            mapping = value;
        }
    }

    private static final class RecordingPublicKeyPort implements TenantPublicKeyPort {
        private final KmsPublicKey publicKey;
        private Integer requestedVersion;

        private RecordingPublicKeyPort(KmsPublicKey publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        public KmsPublicKey read(String keyRef, Integer version) {
            requestedVersion = version;
            return publicKey;
        }

        @Override
        public List<KmsPublicKey> list(String keyRef) {
            return Collections.singletonList(publicKey);
        }
    }

    private static final class RecordingSignerPort implements TenantSignerPort {
        private final KmsSigningResult result;
        private String keyRef;
        private Integer version;
        private byte[] input;

        private RecordingSignerPort(KmsSigningResult result) {
            this.result = result;
        }

        @Override
        public KmsSigningResult sign(String requestedKeyRef, Integer requestedVersion, byte[] signingInput) {
            keyRef = requestedKeyRef;
            version = requestedVersion;
            input = Arrays.copyOf(signingInput, signingInput.length);
            return result;
        }
    }

    private static final class FixedPayloadCodec implements LicensePayloadCodec {
        private final String payload;

        private FixedPayloadCodec(String payload) {
            this.payload = payload;
        }

        @Override
        public String encodeV1(LicenseClaims claims) {
            return payload;
        }
    }
}
