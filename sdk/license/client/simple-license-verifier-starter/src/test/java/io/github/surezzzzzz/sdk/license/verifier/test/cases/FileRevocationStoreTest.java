package io.github.surezzzzzz.sdk.license.verifier.test.cases;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.ProtocolHeader;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationPackageCodec;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.FileRevocationStore;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.InMemoryTrustStore;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 吊销仓真实文件回归：独立实例可见、并发安装、失败保留旧快照和同号内容绑定。
 *
 * <p>安装输入均通过真实 P-256 签名验证；拒写场景使用本机 DOS 文件只读属性。
 * 两实例为同 JVM 独立仓，不冒充两个进程或网络文件系统的验证。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class FileRevocationStoreTest {

    @TempDir
    private Path directory;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    @Test
    void existingObserverSeesAnotherInstancesCommittedEntriesAndWatermarks() throws Exception {
        FileRevocationStore writer = new FileRevocationStore(directory);
        FileRevocationStore observer = new FileRevocationStore(directory);
        assertThat(observer.installedSequence("issuer-1", "product-1")).isEmpty();
        assertThat(observer.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isFalse();

        writer.install(verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1"));

        log.info("核对已有独立仓可见另一实例的已提交吊销和水位");
        assertThat(observer.installedSequence("issuer-1", "product-1")).hasValue(1L);
        assertThat(observer.installedIssuedAt("issuer-1", "product-1")).contains(1000L);
        assertThat(observer.isRevoked("CREDENTIAL", "credential-1")).isTrue();
        assertThat(observer.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isTrue();
        assertThat(observer.isRevoked("issuer-2", "product-1", "CREDENTIAL", "credential-1")).isFalse();
    }

    @Test
    void readOnlyDestinationDoesNotAdvanceStateAndRetryPersistsTheSameCandidate() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        store.install(verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1"));
        RevocationPackageCodec.VerifiedPackage next =
                verified("issuer-1", "product-1", 2L, 1001L, "snapshot-2", "credential-1", "credential-2");
        Path file = directory.resolve("revocations.properties");
        DosFileAttributeView protection = Files.getFileAttributeView(file, DosFileAttributeView.class);
        assertThat(protection).as("本机拒写回归需要 DOS 文件只读属性，不静默跳过").isNotNull();
        protection.setReadOnly(true);
        try {
            log.info("核对真实目标文件拒绝原子替换时，候选序号、时间和条目均未提交");
            assertThatThrownBy(() -> store.install(next)).isInstanceOf(IllegalStateException.class);
            assertThat(store.installedSequence("issuer-1", "product-1")).hasValue(1L);
            assertThat(store.installedIssuedAt("issuer-1", "product-1")).contains(1000L);
            assertThat(store.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isTrue();
            assertThat(store.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-2")).isFalse();
        } finally {
            protection.setReadOnly(false);
        }

        store.install(next);

        assertThat(store.installedSequence("issuer-1", "product-1")).hasValue(2L);
        FileRevocationStore reloaded = new FileRevocationStore(directory);
        assertThat(reloaded.installedIssuedAt("issuer-1", "product-1")).contains(1001L);
        assertThat(reloaded.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-2")).isTrue();
    }

    @Test
    void equalSequenceAndSnapshotMustAlsoMatchIssuedAtAndEntries() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        RevocationPackageCodec.VerifiedPackage original =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1");
        store.install(original);
        byte[] before = Files.readAllBytes(directory.resolve("revocations.properties"));
        store.install(original);
        assertThat(Files.readAllBytes(directory.resolve("revocations.properties"))).isEqualTo(before);

        RevocationPackageCodec.VerifiedPackage changedEntries =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-2");
        RevocationPackageCodec.VerifiedPackage changedTime =
                verified("issuer-1", "product-1", 1L, 1001L, "snapshot-1", "credential-1");
        RevocationPackageCodec.VerifiedPackage changedSnapshot =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-other", "credential-1");
        for (RevocationPackageCodec.VerifiedPackage changed :
                java.util.Arrays.asList(changedEntries, changedTime, changedSnapshot)) {
            assertThatThrownBy(() -> store.install(changed)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(store.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-2")).isFalse();
        assertThat(Files.readAllBytes(directory.resolve("revocations.properties"))).isEqualTo(before);
    }

    @Test
    void concurrentIndependentStoresPreserveBothScopesWithoutOverlappingFileLock() throws Exception {
        FileRevocationStore first = new FileRevocationStore(directory);
        FileRevocationStore second = new FileRevocationStore(directory);
        RevocationPackageCodec.VerifiedPackage left =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1");
        RevocationPackageCodec.VerifiedPackage right =
                verified("issuer-2", "product-2", 1L, 1001L, "snapshot-2", "credential-2");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = workers.submit(() -> {
                await(start);
                first.install(left);
            });
            Future<?> b = workers.submit(() -> {
                await(start);
                second.install(right);
            });
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
            assertThat(first.isRevoked("issuer-2", "product-2", "CREDENTIAL", "credential-2")).isTrue();
            assertThat(second.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isTrue();
            assertThat(first.installedSequence("issuer-1", "product-1")).hasValue(1L);
            assertThat(second.installedSequence("issuer-2", "product-2")).hasValue(1L);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void completeHigherSequenceKeepsPriorTombstonesAndLowerSequenceIsRejected() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        RevocationPackageCodec.VerifiedPackage original =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1");
        store.install(original);
        store.install(verified("issuer-1", "product-1", 2L, 1001L, "snapshot-2", "credential-1", "credential-2"));

        assertThatThrownBy(() -> store.install(original)).isInstanceOf(IllegalArgumentException.class);
        FileRevocationStore reloaded = new FileRevocationStore(directory);
        assertThat(reloaded.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isTrue();
        assertThat(reloaded.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-2")).isTrue();
        assertThat(reloaded.installedSequence("issuer-1", "product-1")).hasValue(2L);
    }

    @Test
    void incompleteHigherSequenceCannotRefreshTheKnownSnapshot() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        store.install(verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1"));
        RevocationPackageCodec.VerifiedPackage incomplete =
                verified("issuer-1", "product-1", 2L, 1001L, "snapshot-2", "credential-2");

        assertThatThrownBy(() -> store.install(incomplete)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.installedSequence("issuer-1", "product-1")).hasValue(1L);
        assertThat(store.installedIssuedAt("issuer-1", "product-1")).contains(1000L);
        assertThat(store.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isTrue();
        assertThat(store.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-2")).isFalse();
    }

    @Test
    void missingCommittedFileFailsClosedRatherThanReturningAnEmptyStore() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        store.install(verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1"));
        Files.delete(directory.resolve("revocations.properties"));

        assertThatThrownBy(() -> store.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.installedSequence("issuer-1", "product-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void previouslyObservedWatermarkRollbackIsRejected() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        store.install(verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1"));
        Path file = directory.resolve("revocations.properties");
        byte[] older = Files.readAllBytes(file);
        store.install(verified("issuer-1", "product-1", 2L, 1001L, "snapshot-2", "credential-1", "credential-2"));
        Files.write(file, older);

        assertThatThrownBy(() -> store.installedSequence("issuer-1", "product-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.isRevoked("CREDENTIAL", "credential-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyFileWithoutDigestOnlyAllowsExactFullSnapshotReplay() throws Exception {
        FileRevocationStore writer = new FileRevocationStore(directory);
        RevocationPackageCodec.VerifiedPackage original =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1");
        writer.install(original);
        Path file = directory.resolve("revocations.properties");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        properties.keySet().removeIf(key -> String.valueOf(key).endsWith(".snapshotDigest"));
        try (Writer output = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(output, "legacy test fixture");
        }
        FileRevocationStore legacy = new FileRevocationStore(directory);
        legacy.install(original);
        RevocationPackageCodec.VerifiedPackage changed =
                verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-2");

        assertThatThrownBy(() -> legacy.install(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(legacy.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-1")).isTrue();
        assertThat(legacy.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-2")).isFalse();
    }

    @Test
    void corruptIndexesAndOrphanMetadataAreRejectedAfterRestart() throws Exception {
        FileRevocationStore store = new FileRevocationStore(directory);
        store.install(verified("issuer-1", "product-1", 1L, 1000L, "snapshot-1", "credential-1"));
        Path file = directory.resolve("revocations.properties");
        Properties original = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            original.load(reader);
        }
        for (int scenario = 0; scenario < 5; scenario++) {
            Properties corrupted = new Properties();
            corrupted.putAll(original);
            if (scenario == 0) {
                corrupted.keySet().removeIf(key -> String.valueOf(key).endsWith(".sequence"));
            } else if (scenario == 1) {
                corrupted.setProperty("scoped.malformed-index", "900");
            } else if (scenario == 2) {
                corrupted.setProperty("entry." + Base64Url.encode("unknown\u0000id".getBytes(StandardCharsets.UTF_8)),
                        "900");
            } else if (scenario == 3) {
                corrupted.setProperty("unknownField", "value");
            } else {
                corrupted.keySet().removeIf(key -> String.valueOf(key).startsWith("scoped."));
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                corrupted.store(writer, "corrupt test fixture");
            }
            log.info("核对损坏快照重启拒绝：scenario={}", scenario);
            assertThatThrownBy(() -> new FileRevocationStore(directory)).isInstanceOf(IllegalStateException.class);
        }
    }

    private RevocationPackageCodec.VerifiedPackage verified(String issuer, String product, long sequence,
                                                            long issuedAt, String snapshotId, String... targets)
            throws Exception {
        ObjectNode part = common(issuer, product, sequence, issuedAt, snapshotId);
        part.put("partIndex", 0);
        ArrayNode entries = part.putArray("entries");
        for (String target : targets) {
            entries.addObject().put("targetKind", "CREDENTIAL").put("targetId", target).put("revokedAt", 900L);
        }
        String partJws = sign(part, ProtocolHeader.TYP_REVOCATION);
        ObjectNode manifest = common(issuer, product, sequence, issuedAt, snapshotId);
        manifest.put("partCount", 1);
        ArrayNode digests = manifest.putArray("partDigests");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(partJws.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 255));
        }
        digests.add(hex.toString());
        InMemoryTrustStore trust = new InMemoryTrustStore();
        trust.install(TrustedPublicKey.of(issuer, product, "kid-test", TrustedPublicKey.Purpose.REVOCATION,
                1L, keyPair.getPublic().getEncoded()));
        return RevocationPackageCodec.verify(sign(manifest, ProtocolHeader.TYP_REVOCATION_MANIFEST),
                Collections.singletonList(partJws), issuer, product, trust, 2000L);
    }

    private static ObjectNode common(String issuer, String product, long sequence, long issuedAt, String snapshotId) {
        ObjectNode node = StrictJson.mapper().createObjectNode();
        node.put("protocolVersion", 2);
        node.putObject("scope").put("issuerId", issuer).put("productId", product);
        node.put("sequence", sequence);
        node.put("issuedAt", issuedAt);
        node.put("snapshotId", snapshotId);
        return node;
    }

    private String sign(ObjectNode payload, String typ) throws Exception {
        ObjectNode header = StrictJson.mapper().createObjectNode();
        header.put("alg", "ES256").put("kid", "kid-test").put("typ", typ);
        String input = Base64Url.encode(StrictJson.mapper().writeValueAsBytes(header)) + "."
                + Base64Url.encode(StrictJson.mapper().writeValueAsBytes(payload));
        return input + "." + Base64Url.encode(Es256Primitives.signForTesting(
                input.getBytes(StandardCharsets.US_ASCII), keyPair.getPrivate()));
    }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发协调未在截止时间内完成");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发协调被中断", interrupted);
        }
    }
}
