package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.core.spi.KeyMaterialPort;
import io.github.surezzzzzz.sdk.license.server.service.SigningProfileRegistrationService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 签名档案登记六场景验收（真实 MySQL）：不可变历史轮换、并发轮换原子性、
 * 钥材料不匹配拒登记、签名服务不可用回滚、旧 kid 历史快照验签（数据库恢复后的信任链）。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = {PersistenceTestApplication.class,
        SigningProfileRegistrationTest.RegistrationTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SigningProfileRegistrationTest {

    /** 可编程钥材料桩：每次调用递增计数，行为可切换（正常/各错形态/不可用）。 */
    static final class ScriptedKeyMaterialPort implements KeyMaterialPort {

        interface Behavior {
            KeyMaterial read(String keyRef, int keyVersion);
        }

        volatile Behavior behavior = (keyRef, keyVersion) -> {
            throw new IllegalStateException("行为未设定");
        };
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public KeyMaterial readKeyMaterial(String keyRef, int keyVersion) {
            calls.incrementAndGet();
            return behavior.read(keyRef, keyVersion);
        }
    }

    @TestConfiguration
    static class RegistrationTestConfig {

        @Bean
        ScriptedKeyMaterialPort scriptedKeyMaterialPort() {
            return new ScriptedKeyMaterialPort();
        }

        @Bean
        SigningProfileRegistrationService registrationService(SigningProfileRepository profiles,
                                                              ProductRepository products,
                                                              ScriptedKeyMaterialPort port) {
            return new SigningProfileRegistrationService(profiles, products, port);
        }
    }

    @Autowired
    private SigningProfileRepository profiles;

    @Autowired
    private ProductRepository products;

    @Autowired
    private SigningProfileRegistrationService registration;

    @Autowired
    private ScriptedKeyMaterialPort keyMaterial;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String productId;
    private KeyPair keyPairA;
    private KeyPair keyPairB;

    @BeforeEach
    void seed() throws Exception {
        productId = "prod-" + UUID.randomUUID().toString().substring(0, 8);
        products.save(new ProductEntity(productId, "iss-1", "code-" + productId, "display", "{}", "[]"));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        keyPairA = generator.generateKeyPair();
        keyPairB = generator.generateKeyPair();
        keyMaterial.calls.set(0);
        goodMaterial();
        ensureActiveMarkerGuard();
    }

    /**
     * hbm2ddl 不生成 schema.sql 的 active_marker 兜底列；测试表补齐与生产 DDL 同构的
     * “在用档案唯一”数据库级保证（并发首建/轮换的最终防线）。
     */
    private void ensureActiveMarkerGuard() {
        Integer present = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                        + " AND table_name = 'license_signing_profile' AND column_name = 'active_marker'",
                Integer.class);
        if (present != null && present == 0) {
            jdbc.execute("ALTER TABLE license_signing_profile"
                    + " ADD COLUMN active_marker INT GENERATED ALWAYS AS"
                    + " (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,"
                    + " ADD UNIQUE KEY uk_license_signing_profile_active"
                    + " (product_id, purpose, active_marker)");
        }
    }

    @AfterEach
    void cleanup() {
        profiles.deleteAll(profiles.findAll());
        products.deleteById(productId);
    }

    /** 正常材料行为：按请求 keyRef 生成对应钥的 SPKI 回显（A 钥=keyRef-a，B 钥=keyRef-b）。 */
    private void goodMaterial() {
        keyMaterial.behavior = (keyRef, keyVersion) -> {
            byte[] spki = "keyRef-a".equals(keyRef)
                    ? keyPairA.getPublic().getEncoded() : keyPairB.getPublic().getEncoded();
            return new KeyMaterialPort.KeyMaterial(keyRef, keyVersion, "ES256", "ACTIVE", "ACTIVE", spki);
        };
    }

    // ===== 场景 1：不可变历史——轮换留 RETIRED 旧档案 =====

    @Test
    void rotationRetiresOldProfileAndKeepsHistory() {
        SigningProfileRegistrationService.Registration first =
                registration.register(productId, "CREDENTIAL", "kid-a", "keyRef-a", 3, null, null);
        assertThat(first.rotatedFrom).isNull();

        long oldRowVersion = first.profile.getRowVersion();
        SigningProfileRegistrationService.Registration second =
                registration.register(productId, "CREDENTIAL", "kid-b", "keyRef-b", 1,
                        first.profile.getProfileId(), oldRowVersion);

        assertThat(second.rotatedFrom).isEqualTo(first.profile.getProfileId());
        List<SigningProfileEntity> history = profiles.findAll();
        assertThat(history).hasSize(2);
        Optional<SigningProfileEntity> retired = history.stream()
                .filter(p -> p.getProfileId().equals(first.profile.getProfileId())).findFirst();
        assertThat(retired).isPresent();
        assertThat(retired.get().getStatus()).isEqualTo("RETIRED");
        // 旧档案公钥快照仍在库（旧 kid 历史验签与审计的信任链来源）
        assertThat(retired.get().getRegisteredPublicKeyHex()).isNotEmpty();
        Optional<SigningProfileEntity> active = profiles
                .findByProductIdAndPurposeAndStatus(productId, "CREDENTIAL", "ACTIVE");
        assertThat(active).isPresent();
        assertThat(active.get().getKid()).isEqualTo("kid-b");
    }

    // ===== 场景 2：并发轮换——恰一个成功，库中恰一个 ACTIVE =====

    @Test
    void concurrentRotationAdmitsExactlyOneWinner() throws Exception {
        SigningProfileRegistrationService.Registration first =
                registration.register(productId, "CREDENTIAL", "kid-a", "keyRef-a", 3, null, null);
        long expected = first.profile.getRowVersion();
        final String firstProfileId = first.profile.getProfileId();

        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> rotate = () -> registration.register(
                productId, "CREDENTIAL", "kid-" + UUID.randomUUID().toString().substring(0, 6),
                "keyRef-b", 2, firstProfileId, expected);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> firstAttempt = pool.submit(rotate);
        Future<Object> secondAttempt = pool.submit(rotate);
        start.countDown();
        // 悲观锁+expectedProfileId/expectedRowVersion 双 CAS：并发轮换恰一个成功，
        // 另一个必 409（后到者看到新在用档案，期望身份不匹配；锁等待超时同样归一 409）
        int succeeded = 0;
        for (Future<Object> attempt : java.util.Arrays.asList(firstAttempt, secondAttempt)) {
            try {
                assertThat(attempt.get(30, TimeUnit.SECONDS)).isNotNull();
                succeeded++;
            } catch (java.util.concurrent.ExecutionException conflict) {
                assertThat(conflict.getCause()).isInstanceOf(LicenseHttpException.class);
                assertThat(((LicenseHttpException) conflict.getCause()).getStatus()).isEqualTo(409);
            }
        }
        assertThat(succeeded).isEqualTo(1);
        List<SigningProfileEntity> actives = profiles.findAll();
        long activeCount = actives.stream()
                .filter(p -> "ACTIVE".equals(p.getStatus())).count();
        assertThat(activeCount).isEqualTo(1L);
        pool.shutdownNow();
    }

    // ===== 场景 3：钥材料不匹配拒登记（四形态逐项） =====

    @Test
    void materialMismatchRejectedPerCategory() {
        // 3a 版本回显不符
        keyMaterial.behavior = (keyRef, keyVersion) -> new KeyMaterialPort.KeyMaterial(
                keyRef, keyVersion + 1, "ES256", "ACTIVE", "ACTIVE",
                keyPairA.getPublic().getEncoded());
        assertRejected422("版本回显不符");

        // 3b keyRef 回显不符
        keyMaterial.behavior = (keyRef, keyVersion) -> new KeyMaterialPort.KeyMaterial(
                "other-key", keyVersion, "ES256", "ACTIVE", "ACTIVE",
                keyPairA.getPublic().getEncoded());
        assertRejected422("keyRef 回显不符");

        // 3c 算法非 ES256
        keyMaterial.behavior = (keyRef, keyVersion) -> new KeyMaterialPort.KeyMaterial(
                keyRef, keyVersion, "RS256", "ACTIVE", "ACTIVE",
                keyPairA.getPublic().getEncoded());
        assertRejected422("算法不符");

        // 3d 版本状态不可签（RETIRED 仅历史验签）
        keyMaterial.behavior = (keyRef, keyVersion) -> new KeyMaterialPort.KeyMaterial(
                keyRef, keyVersion, "ES256", "RETIRED", "ACTIVE",
                keyPairA.getPublic().getEncoded());
        assertRejected422("版本状态不可签");

        // 3e SPKI 非 P-256 形态（合法 X.509 但截断）
        keyMaterial.behavior = (keyRef, keyVersion) -> new KeyMaterialPort.KeyMaterial(
                keyRef, keyVersion, "ES256", "ACTIVE", "ACTIVE",
                java.util.Arrays.copyOf(keyPairA.getPublic().getEncoded(), 45));
        assertRejected422("SPKI 形态不符");

        // 拒绝后库中无残留档案
        assertThat(profiles.findAll()).isEmpty();
        assertThat(keyMaterial.calls.get()).isEqualTo(5);
    }

    // ===== 场景 4：签名服务不可用——503 且事务回滚无半登记 =====

    @Test
    void keyServiceUnavailableFailsCleanWithoutPartialState() {
        SigningProfileRegistrationService.Registration first =
                registration.register(productId, "CREDENTIAL", "kid-a", "keyRef-a", 3, null, null);
        keyMaterial.behavior = (keyRef, keyVersion) -> {
            throw new RuntimeException("connect timeout");
        };
        assertThatThrownBy(() -> registration.register(productId, "CREDENTIAL", "kid-b", "keyRef-b", 1,
                first.profile.getProfileId(), first.profile.getRowVersion()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(503);
        // 回滚：旧档案未退役、无新行（不可用不产生半登记状态）
        Optional<SigningProfileEntity> stillActive = profiles
                .findByProductIdAndPurposeAndStatus(productId, "CREDENTIAL", "ACTIVE");
        assertThat(stillActive).isPresent();
        assertThat(stillActive.get().getKid()).isEqualTo("kid-a");
        assertThat(profiles.findAll()).hasSize(1);
    }

    // ===== 场景 5：旧 kid 历史验签（数据库恢复后信任链不灭） =====

    @Test
    void retiredSnapshotStillVerifiesOldKidSignatureAfterRotation() throws Exception {
        SigningProfileRegistrationService.Registration first =
                registration.register(productId, "CREDENTIAL", "kid-a", "keyRef-a", 3, null, null);
        byte[] signingInput = "header.payload.".getBytes(StandardCharsets.US_ASCII);
        byte[] oldSignature = Es256Primitives.signForTesting(signingInput, keyPairA.getPrivate());

        registration.register(productId, "CREDENTIAL", "kid-b", "keyRef-b", 1,
                first.profile.getProfileId(), first.profile.getRowVersion());

        // 轮换后从库中回读 RETIRED 档案快照验旧 kid 签名：仍通过（历史凭据不受轮换影响）
        SigningProfileEntity retired = profiles.findById(first.profile.getProfileId()).orElse(null);
        assertThat(retired).isNotNull();
        assertThat(retired.getStatus()).isEqualTo("RETIRED");
        assertThat(Es256Primitives.verify(signingInput, oldSignature,
                Es256Primitives.parseSpkiPublicKey(hexToBytes(retired.getRegisteredPublicKeyHex())))).isTrue();

        // 新档案快照验旧 kid 签名：必须失败（跨钥拦截，验签侧公钥与凭据 kid 不再错配放行）
        SigningProfileEntity active = profiles
                .findByProductIdAndPurposeAndStatus(productId, "CREDENTIAL", "ACTIVE").orElse(null);
        assertThat(active).isNotNull();
        assertThat(Es256Primitives.verify(signingInput, oldSignature,
                Es256Primitives.parseSpkiPublicKey(hexToBytes(active.getRegisteredPublicKeyHex())))).isFalse();
    }

    // ===== 场景 6（前置）：轮换必须带 expectedRowVersion（并发切换原子性的受理门槛） =====

    @Test
    void rotationWithoutExpectedRowVersionRejected() {
        registration.register(productId, "CREDENTIAL", "kid-a", "keyRef-a", 3, null, null);
        assertThatThrownBy(() -> registration.register(productId, "CREDENTIAL", "kid-b", "keyRef-b", 1,
                null, null))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(409);
        assertThat(profiles.findAll()).hasSize(1);
    }

    private void assertRejected422(String label) {
        assertThatThrownBy(() -> registration.register(productId, "CREDENTIAL",
                "kid-" + UUID.randomUUID().toString().substring(0, 6), "keyRef-a", 3, null, null))
                .as(label)
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
