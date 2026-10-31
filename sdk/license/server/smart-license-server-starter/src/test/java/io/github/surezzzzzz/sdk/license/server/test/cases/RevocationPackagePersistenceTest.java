package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationScopeEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.event.RevocationPackageCompleted;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationPackagePartRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationPackageRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationScopeRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.RevocationPackageService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实 MySQL 吊销范围回归：首次初始化的并发事务、幂等重放和失败水位持久化。
 *
 * <p>两个独立服务对象通过生产事务注解执行，线程拥有各自数据库事务；签名替身用真实 P-256 钥签名。
 * 这证明数据库协调，不能替代多容器或真实 KMS 验收。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RevocationPackagePersistenceTest {

    @Autowired
    private ProductRepository products;
    @Autowired
    private RevocationRepository revocations;
    @Autowired
    private RevocationScopeRepository scopes;
    @Autowired
    private RevocationPackageRepository packages;
    @Autowired
    private RevocationPackagePartRepository parts;
    @Autowired
    private SigningProfileRepository profiles;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate jdbc;
    private String productId;
    private String profileId;
    private KeyPair keyPair;
    private final List<RevocationPackageCompleted> events = new CopyOnWriteArrayList<RevocationPackageCompleted>();
    private final List<String> committedStates = new CopyOnWriteArrayList<String>();

    @BeforeEach
    void setUp() throws Exception {
        events.clear();
        committedStates.clear();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        String suffix = UUID.randomUUID().toString();
        productId = "product-rp-" + suffix;
        profileId = "profile-rp-" + suffix;
        products.saveAndFlush(new ProductEntity(productId, "issuer-example", "rp-" + suffix,
                "示例产品", "[]", "[]"));
        profiles.saveAndFlush(new SigningProfileEntity(profileId, productId, "REVOCATION",
                "kid-rp-" + suffix, "key-example", 1, hex(keyPair.getPublic().getEncoded())));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM license_revocation_package_part WHERE package_id IN "
                + "(SELECT package_id FROM license_revocation_package WHERE product_id=?)", productId);
        jdbc.update("DELETE FROM license_revocation_package WHERE product_id=?", productId);
        jdbc.update("DELETE FROM license_revocation_scope WHERE product_id=?", productId);
        profiles.deleteById(profileId);
        products.deleteById(productId);
    }

    @Test
    void firstScopeConcurrentReplayCommitsOnePackageWithoutRollbackOnly() throws Exception {
        assertThat(scopes.findByIssuerIdAndProductId("issuer-example", productId)).isEmpty();
        CountDownLatch firstSigning = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        LicenseSigner firstSigner = mock(LicenseSigner.class);
        when(firstSigner.sign(anyString(), anyString(), any(SigningInput.class))).thenAnswer(invocation -> {
            firstSigning.countDown();
            requireArrival(releaseFirst);
            return Es256Primitives.signForTesting(((SigningInput) invocation.getArgument(2)).getBytes(),
                    keyPair.getPrivate());
        });
        LicenseSigner secondSigner = signer();
        RevocationPackageService first = service(firstSigner);
        RevocationPackageService second = service(secondSigner);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<RevocationPackageEntity> a = workers.submit(() -> first.create(productId, "same-" + productId));
            requireArrival(firstSigning);
            // 签名被暂停时，事务尚未提交，终态事件不可提前暴露。
            assertThat(events).isEmpty();
            Future<RevocationPackageEntity> b = workers.submit(() -> {
                // 首个事务已经插入但尚未提交；另一个连接仍看不到范围，必须走同一首次创建入口。
                assertThat(scopes.findByIssuerIdAndProductId("issuer-example", productId)).isEmpty();
                secondStarted.countDown();
                return second.create(productId, "same-" + productId);
            });
            requireArrival(secondStarted);
            releaseFirst.countDown();

            RevocationPackageEntity left = a.get(20, TimeUnit.SECONDS);
            RevocationPackageEntity right = b.get(20, TimeUnit.SECONDS);

            log.info("首次吊销范围并发重放完成：双方事务成功，同一包={}",
                    left.getPackageId().equals(right.getPackageId()));
            assertThat(left.getPackageId()).isEqualTo(right.getPackageId());
            assertThat(right.getState()).isEqualTo("READY");
            assertThat(right.getSequence()).isEqualTo(1L);
            assertThat(count("license_revocation_scope")).isEqualTo(1L);
            assertThat(count("license_revocation_package")).isEqualTo(1L);
            assertThat(scopes.findByIssuerIdAndProductId("issuer-example", productId))
                    .isPresent().get().extracting(RevocationScopeEntity::getNextSequence).isEqualTo(1L);
            assertThat(scopes.findByIssuerIdAndProductId("issuer-example", productId).get().getInstalledSequence())
                    .isEqualTo(1L);
            verify(firstSigner, times(2)).sign(anyString(), anyString(), any(SigningInput.class));
            verify(secondSigner, times(0)).sign(anyString(), anyString(), any(SigningInput.class));
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getPackageId()).isEqualTo(left.getPackageId());
            assertThat(events.get(0).getOperationId()).isEqualTo(left.getOperationId());
            assertThat(events.get(0).getState()).isEqualTo("READY");
            assertThat(events.get(0).getSequence()).isEqualTo(1L);
            assertThat(committedStates).containsExactly("READY");
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void invalid64ByteSignatureCommitsFailedEventWithoutAdvancingSuccessfulExport() {
        LicenseSigner invalid = mock(LicenseSigner.class);
        when(invalid.sign(anyString(), anyString(), any(SigningInput.class))).thenReturn(new byte[64]);
        String digest = "invalid-local-signature-" + productId;

        assertThatThrownBy(() -> service(invalid).create(productId, digest))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
        RevocationPackageEntity failed = packages.findByIdempotencyDigest(digest).get();
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(failed.getManifestJws()).isNull();
        assertThat(parts.findByPackageIdOrderByPartIndexAsc(failed.getPackageId())).isEmpty();
        RevocationScopeEntity scope = scopes.findByIssuerIdAndProductId("issuer-example", productId).get();
        assertThat(scope.getNextSequence()).isEqualTo(1L);
        assertThat(scope.getInstalledSequence()).isZero();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getState()).isEqualTo("FAILED");
        assertThat(committedStates).containsExactly("FAILED");
    }

    @Test
    void firstScopeConcurrentDistinctRequestsCommitDifferentMonotonicSequences() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        RevocationPackageService first = service(signer());
        RevocationPackageService second = service(signer());
        try {
            Future<RevocationPackageEntity> a = workers.submit(() -> {
                requireArrival(start);
                return first.create(productId, "first-" + productId);
            });
            Future<RevocationPackageEntity> b = workers.submit(() -> {
                requireArrival(start);
                return second.create(productId, "second-" + productId);
            });
            start.countDown();

            RevocationPackageEntity left = a.get(20, TimeUnit.SECONDS);
            RevocationPackageEntity right = b.get(20, TimeUnit.SECONDS);

            assertThat(left.getPackageId()).isNotEqualTo(right.getPackageId());
            assertThat(new long[]{left.getSequence(), right.getSequence()}).containsExactlyInAnyOrder(1L, 2L);
            assertThat(count("license_revocation_scope")).isEqualTo(1L);
            assertThat(count("license_revocation_package")).isEqualTo(2L);
            RevocationScopeEntity scope = scopes.findByIssuerIdAndProductId("issuer-example", productId).get();
            assertThat(scope.getNextSequence()).isEqualTo(2L);
            assertThat(scope.getTrustVersion()).isEqualTo(2L);
            assertThat(scope.getInstalledSequence()).isEqualTo(2L);
            assertThat(events).hasSize(2);
            assertThat(events).extracting(RevocationPackageCompleted::getSequence)
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(committedStates).containsExactly("READY", "READY");
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void signingFailureCommitsFailedPackageAndTheNextRequestDoesNotReuseWatermarks() {
        LicenseSigner unavailable = mock(LicenseSigner.class);
        when(unavailable.sign(anyString(), anyString(), any(SigningInput.class)))
                .thenThrow(new IllegalStateException("模拟签名依赖不可用"));

        assertThatThrownBy(() -> service(unavailable).create(productId, "failure-" + productId))
                .isInstanceOf(IllegalStateException.class);
        RevocationPackageEntity failed = packages.findByIdempotencyDigest("failure-" + productId).get();
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(failed.getSequence()).isEqualTo(1L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getState()).isEqualTo("FAILED");
        assertThat(events.get(0).getReasonCategory()).isEqualTo("IllegalStateException");
        assertThat(committedStates).containsExactly("FAILED");
        assertThat(scopes.findByIssuerIdAndProductId("issuer-example", productId).get().getInstalledSequence())
                .isZero();
        RevocationPackageEntity replay = service(unavailable).create(productId, "failure-" + productId);
        assertThat(replay.getPackageId()).isEqualTo(failed.getPackageId());
        assertThat(events).hasSize(1);
        verify(unavailable, times(1)).sign(anyString(), anyString(), any(SigningInput.class));

        RevocationPackageEntity next = service(signer()).create(productId, "after-failure-" + productId);

        assertThat(next.getState()).isEqualTo("READY");
        assertThat(next.getSequence()).isEqualTo(2L);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getState()).isEqualTo("READY");
        assertThat(events.get(1).getReasonCategory()).isNull();
        assertThat(committedStates).containsExactly("FAILED", "READY");
        RevocationScopeEntity scope = scopes.findByIssuerIdAndProductId("issuer-example", productId).get();
        assertThat(scope.getNextSequence()).isEqualTo(2L);
        assertThat(scope.getTrustVersion()).isEqualTo(2L);
        assertThat(scope.getInstalledSequence()).isEqualTo(2L);
        scope.confirmExportedSequence(1L);
        assertThat(scope.getInstalledSequence()).isEqualTo(2L);
        assertThatThrownBy(() -> scope.confirmExportedSequence(3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scope.confirmExportedSequence(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(scope.getInstalledSequence()).isEqualTo(2L);
    }

    @Test
    void rolledBackOuterTransactionDoesNotPublishReadyPackage() {
        TransactionTemplate outer = new TransactionTemplate(transactions);
        String digest = "rolled-back-" + productId;
        outer.execute(status -> {
            assertThat(service(signer()).create(productId, digest).getState()).isEqualTo("READY");
            assertThat(events).isEmpty();
            status.setRollbackOnly();
            return null;
        });

        assertThat(events).isEmpty();
        assertThat(packages.findByIdempotencyDigest(digest)).isEmpty();
        assertThat(scopes.findByIssuerIdAndProductId("issuer-example", productId)).isEmpty();
    }

    @Test
    void eventConsumerFailureDoesNotChangeCommittedResultOrMaskSigningFailure() {
        AtomicInteger attempts = new AtomicInteger();
        ApplicationEventPublisher failingConsumer = event -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("模拟事件消费者不可用");
        };
        RevocationPackageEntity ready = service(signer(), failingConsumer)
                .create(productId, "consumer-failure-ready-" + productId);
        assertThat(packages.findById(ready.getPackageId()).get().getState()).isEqualTo("READY");
        assertThat(attempts.get()).isEqualTo(1);

        LicenseSigner unavailable = mock(LicenseSigner.class);
        IllegalStateException original = new IllegalStateException("模拟原始签名故障");
        when(unavailable.sign(anyString(), anyString(), any(SigningInput.class))).thenThrow(original);
        String digest = "consumer-failure-signing-" + productId;
        assertThatThrownBy(() -> service(unavailable, failingConsumer).create(productId, digest))
                .isSameAs(original);
        assertThat(packages.findByIdempotencyDigest(digest).get().getState()).isEqualTo("FAILED");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void entityGeneratedHashColumnsMatchTheChar64Schema() {
        assertThat(jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name='license_revocation_package' "
                        + "AND column_name='manifest_sha256'", String.class)).isEqualTo("char(64)");
        assertThat(jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name='license_revocation_package_part' "
                        + "AND column_name='part_sha256'", String.class)).isEqualTo("char(64)");
        assertThat(jdbc.queryForObject("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                        + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                        + "AND table_name='license_revocation_package' "
                        + "AND index_name='uk_license_revocation_package_sequence' AND non_unique=0", String.class))
                .isEqualTo("issuer_id,product_id,sequence");
    }

    private RevocationPackageService service(LicenseSigner signer) {
        return service(signer, this::observeCommittedPackage);
    }

    private RevocationPackageService service(LicenseSigner signer, ApplicationEventPublisher publisher) {
        RevocationPackageService target = new RevocationPackageService(products, revocations, scopes, packages,
                parts, profiles, signer, publisher);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        // 直接消费生产事务注解，失败保留水位的断言同时验证 noRollbackFor 的真实提交行为。
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (RevocationPackageService) proxy.getProxy();
    }

    /** 提交回调通过独立事务连接回读，避免用原事务缓存误证终态可见性。 */
    private void observeCommittedPackage(Object event) {
        RevocationPackageCompleted completed = (RevocationPackageCompleted) event;
        TransactionTemplate independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String state = independent.execute(status -> packages.findById(completed.getPackageId()).get().getState());
        committedStates.add(state);
        events.add(completed);
    }

    private LicenseSigner signer() {
        LicenseSigner signer = mock(LicenseSigner.class);
        when(signer.sign(anyString(), anyString(), any(SigningInput.class))).thenAnswer(invocation ->
                Es256Primitives.signForTesting(((SigningInput) invocation.getArgument(2)).getBytes(),
                        keyPair.getPrivate()));
        return signer;
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) {
            value.append(String.format("%02x", item & 255));
        }
        return value.toString();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE product_id=?", Long.class, productId);
    }

    private static void requireArrival(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发协调未在截止时间内完成");
        }
    }
}
