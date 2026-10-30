package io.github.surezzzzzz.sdk.license.verifier.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.verifier.clock.SystemClockProvider;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.FileStateStore;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.InMemoryTrustStore;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifier Starter 默认实现断言：信任版本单调、状态文件只增不减、时钟基线。
 *
 * @author surezzzzzz
 */
@Slf4j
class VerifierStarterTest {

    @Test
    void trustStoreOnlyAcceptsHigherVersion() {
        InMemoryTrustStore store = new InMemoryTrustStore();
        TrustedPublicKey v1 = TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 1L, new byte[]{1});
        TrustedPublicKey v2 = TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 2L, new byte[]{1});
        TrustedPublicKey stale = TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 1L, new byte[]{1});
        store.install(v1);
        store.install(v2);
        store.install(stale); // 低版本不回退
        Optional<TrustedPublicKey> found = store.findKey("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL);
        assertThat(found).isPresent();
        assertThat(found.get().getTrustVersion()).isEqualTo(2L);
        assertThat(found.get().getSpki()).containsExactly((byte) 1);
        // 用途隔离：吊销用途查不到凭据钥
        assertThat(store.findKey("i", "p", "k", TrustedPublicKey.Purpose.REVOCATION)).isEmpty();
        assertThat(store.currentTrustVersion("i", "p", TrustedPublicKey.Purpose.CREDENTIAL)).isEqualTo(2L);
    }

    /** 低、同、高版本均不能改绑同一 kid；拒绝后仍保留原公钥和信任版本。 */
    @Test
    void trustStoreRejectsRebindingAtEveryVersion() {
        InMemoryTrustStore store = new InMemoryTrustStore();
        store.install(TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 2L, new byte[]{1}));
        for (long version : new long[]{1L, 2L, 3L}) {
            TrustedPublicKey replacement = TrustedPublicKey.of("i", "p", "k",
                    TrustedPublicKey.Purpose.CREDENTIAL, version, new byte[]{2});
            log.info("验证同 kid 改绑拒绝：候选信任版本={}", version);
            assertThatThrownBy(() -> store.install(replacement))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(ErrorMessage.CRYPTO_SIGNATURE_INVALID);
        }
        Optional<TrustedPublicKey> original = store.findKey("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL);
        assertThat(original).isPresent();
        assertThat(original.get().getTrustVersion()).isEqualTo(2L);
        assertThat(original.get().getSpki()).containsExactly((byte) 1);
    }

    /** kid 全局唯一，不能通过切产品、签发方或用途绕过绑定，也不污染新信任域。 */
    @Test
    void trustStoreRejectsMovingKidToAnotherTrustDomainOrPurpose() {
        InMemoryTrustStore store = new InMemoryTrustStore();
        store.install(TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 1L, new byte[]{1}));
        TrustedPublicKey[] replacements = new TrustedPublicKey[]{
                TrustedPublicKey.of("i2", "p", "k", TrustedPublicKey.Purpose.CREDENTIAL, 2L, new byte[]{1}),
                TrustedPublicKey.of("i", "p2", "k", TrustedPublicKey.Purpose.CREDENTIAL, 2L, new byte[]{1}),
                TrustedPublicKey.of("i", "p", "k", TrustedPublicKey.Purpose.REVOCATION, 2L, new byte[]{1})
        };
        log.info("验证同 kid 的签发方、产品和用途不可迁移");
        for (TrustedPublicKey replacement : replacements) {
            assertThatThrownBy(() -> store.install(replacement))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(store.findKey(replacement.getIssuerId(), replacement.getProductId(), replacement.getKid(),
                    replacement.getPurpose())).isEmpty();
        }
        assertThat(store.currentTrustVersion("i", "p", TrustedPublicKey.Purpose.CREDENTIAL)).isEqualTo(1L);
    }

    /** 正常轮换使用新 kid；新公钥可安装，旧 kid 仍可用于历史验签。 */
    @Test
    void trustStoreRotationKeepsOldKidAndInstallsNewKid() {
        InMemoryTrustStore store = new InMemoryTrustStore();
        store.install(TrustedPublicKey.of("i", "p", "old-kid",
                TrustedPublicKey.Purpose.CREDENTIAL, 1L, new byte[]{1}));
        store.install(TrustedPublicKey.of("i", "p", "new-kid",
                TrustedPublicKey.Purpose.CREDENTIAL, 2L, new byte[]{2}));
        log.info("验证新 kid 轮换与历史公钥保留");
        assertThat(store.findKey("i", "p", "old-kid", TrustedPublicKey.Purpose.CREDENTIAL)
                .get().getSpki()).containsExactly((byte) 1);
        assertThat(store.findKey("i", "p", "new-kid", TrustedPublicKey.Purpose.CREDENTIAL)
                .get().getSpki()).containsExactly((byte) 2);
    }

    @Test
    void stateStoreMonotonicAndSurvivesReload(@TempDir java.nio.file.Path tempDir) {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        String key = store.buildStateKey("i", "p", "c", "g", "activation:a1");
        store.persistMaxTrustedTime(key, 1000L);
        store.persistMaxTrustedTime(key, 900L); // 回退不生效
        assertThat(store.loadMaxTrustedTime(key)).hasValue(1000L);

        // 重载（模拟重启）：状态保留
        FileStateStore reloaded = new FileStateStore(tempDir, "state.properties");
        assertThat(reloaded.loadMaxTrustedTime(key)).hasValue(1000L);
        // 重启后回退仍不生效（持久化水位防一般回滚）
        reloaded.persistMaxTrustedTime(key, 800L);
        assertThat(reloaded.loadMaxTrustedTime(key)).hasValue(1000L);
    }

    @Test
    void independentInstancesCannotOverwriteNewerMonotonicState(@TempDir java.nio.file.Path tempDir) {
        FileStateStore older = new FileStateStore(tempDir, "state.properties");
        FileStateStore newer = new FileStateStore(tempDir, "state.properties");
        String key = older.buildStateKey("i", "p", "c", "g", "activation:a1");
        older.persistMaxTrustedTime(key, 1000L);
        newer.persistMaxTrustedTime(key, 2000L);
        older.persistMaxTrustedTime(key, 1500L);
        log.info("验证独立状态实例回读最新磁盘水位，旧实例不能覆盖更高T_max");
        assertThat(older.loadMaxTrustedTime(key)).hasValue(2000L);
        assertThat(new FileStateStore(tempDir, "state.properties").loadMaxTrustedTime(key)).hasValue(2000L);
    }

    @Test
    void stateKeysRemainDistinctWhenIdentifiersContainSeparators(@TempDir java.nio.file.Path tempDir) {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        String left = store.buildStateKey("issuer:a", "product", "customer", "grant", "subject:x:y");
        String right = store.buildStateKey("issuer", "a:product", "customer", "grant", "subject:x:y");
        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void stateFileCreatedAtomically(@TempDir java.nio.file.Path tempDir) {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        store.persistRevocationSequence("i", "p", 7L);
        assertThat(Files.exists(tempDir.resolve("state.properties"))).isTrue();
        assertThat(store.loadRevocationSequence("i", "p")).hasValue(7L);
    }

    @Test
    void revocationSequenceKeysRemainDistinctWhenIdentifiersContainDots(@TempDir java.nio.file.Path tempDir) {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        store.persistRevocationSequence("issuer.a", "product", 7L);
        store.persistRevocationSequence("issuer", "a.product", 9L);

        assertThat(store.loadRevocationSequence("issuer.a", "product")).hasValue(7L);
        assertThat(store.loadRevocationSequence("issuer", "a.product")).hasValue(9L);
    }

    @Test
    void missingCommittedStateFailsClosedForNewInstance(@TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        String key = store.buildStateKey("i", "p", "c", "g", "activation:a1");
        store.persistMaxTrustedTime(key, 1000L);
        Files.delete(tempDir.resolve("state.properties"));

        assertThatThrownBy(() -> new FileStateStore(tempDir, "state.properties"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("状态文件缺失");
    }

    @Test
    void missingCommittedStateCannotBeRecreatedByExistingInstance(@TempDir java.nio.file.Path tempDir)
            throws java.io.IOException {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        String key = store.buildStateKey("i", "p", "c", "g", "activation:a1");
        store.persistMaxTrustedTime(key, 1000L);
        Files.delete(tempDir.resolve("state.properties"));

        assertThatThrownBy(() -> store.persistMaxTrustedTime(key, 2000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("状态文件缺失");
    }

    @Test
    void firstPersistenceCreatesStateAndMarker(@TempDir java.nio.file.Path tempDir) {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        String key = store.buildStateKey("i", "p", "c", "g", "activation:a1");

        assertThat(Files.exists(tempDir.resolve("state.properties"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("state.properties.lock"))).isFalse();

        store.persistMaxTrustedTime(key, 1000L);

        assertThat(Files.exists(tempDir.resolve("state.properties"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("state.properties.lock"))).isTrue();
    }

    @Test
    void committedStateWithoutMarkerIsLoadedAndMarkerIsMigrated(@TempDir java.nio.file.Path tempDir)
            throws java.io.IOException {
        FileStateStore store = new FileStateStore(tempDir, "state.properties");
        String key = store.buildStateKey("i", "p", "c", "g", "activation:a1");
        store.persistMaxTrustedTime(key, 1000L);
        Files.delete(tempDir.resolve("state.properties.lock"));

        FileStateStore reloaded = new FileStateStore(tempDir, "state.properties");

        assertThat(reloaded.loadMaxTrustedTime(key)).hasValue(1000L);
        assertThat(Files.exists(tempDir.resolve("state.properties.lock"))).isTrue();
    }

    /** 真实文件系统写入失败不提交内存水位；修复路径后同值重试必须写盘。 */
    @Test
    void failedPersistenceDoesNotAdvanceCacheAndSameValueRetryWritesDisk(
            @TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path blockedDirectory = tempDir.resolve("blocked");
        Files.createFile(blockedDirectory);
        FileStateStore store = new FileStateStore(blockedDirectory, "state.properties");
        String key = store.buildStateKey("i", "p", "c", "g", "activation:a1");
        log.info("验证文件系统拒写时缓存保持旧水位，恢复后同值重试可持久化");
        assertThatThrownBy(() -> store.persistMaxTrustedTime(key, 1000L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.loadMaxTrustedTime(key)).isEmpty();
        Files.delete(blockedDirectory);
        store.persistMaxTrustedTime(key, 1000L);
        assertThat(store.loadMaxTrustedTime(key)).hasValue(1000L);
        FileStateStore reloaded = new FileStateStore(blockedDirectory, "state.properties");
        assertThat(reloaded.loadMaxTrustedTime(key)).hasValue(1000L);
    }

    @Test
    void clockProviderBaselineNotSuspect() {
        SystemClockProvider provider = new SystemClockProvider();
        assertThat(provider.isClockSuspect()).isFalse(); // 建立基线
        assertThat(provider.isClockSuspect()).isFalse(); // 紧邻第二次也不疑似
        assertThat(provider.currentEffectiveNow()).isPositive();
    }

    /** 回拨被观察后，即使墙钟看似追回也持续拒绝，不因下一次采样自动清除。 */
    @Test
    void clockSuspectRemainsLatchedAfterWallClockRollback() {
        AtomicLong wallMillis = new AtomicLong(10_000L);
        AtomicLong monotonicNanos = new AtomicLong(0L);
        SystemClockProvider provider = new SystemClockProvider(wallMillis::get, monotonicNanos::get);
        assertThat(provider.isClockSuspect()).isFalse();
        wallMillis.set(9_000L);
        monotonicNanos.set(1_000_000_000L);
        log.info("验证墙钟回拨疑似在当前实例持续保持");
        assertThat(provider.isClockSuspect()).isTrue();
        wallMillis.set(12_000L);
        monotonicNanos.set(3_000_000_000L);
        assertThat(provider.isClockSuspect()).isTrue();
        assertThat(provider.isClockSuspect()).isTrue();
    }

    /** 单调增量明显大于墙钟增量时也锁存；后续增量一致不能自行解除。 */
    @Test
    void clockSuspectRemainsLatchedAfterMonotonicMismatch() {
        AtomicLong wallMillis = new AtomicLong(10_000L);
        AtomicLong monotonicNanos = new AtomicLong(0L);
        SystemClockProvider provider = new SystemClockProvider(wallMillis::get, monotonicNanos::get);
        assertThat(provider.isClockSuspect()).isFalse();
        wallMillis.set(11_000L);
        monotonicNanos.set(7_000_000_000L);
        log.info("验证单调时钟与墙钟明显倒挂后持续拒绝");
        assertThat(provider.isClockSuspect()).isTrue();
        wallMillis.set(12_000L);
        monotonicNanos.set(8_000_000_000L);
        assertThat(provider.isClockSuspect()).isTrue();
    }

    /** 健康采样只滚动基线，正常时间推进不产生疑似。 */
    @Test
    void healthyClockSamplesContinueWithoutSuspicion() {
        AtomicLong wallMillis = new AtomicLong(10_000L);
        AtomicLong monotonicNanos = new AtomicLong(0L);
        SystemClockProvider provider = new SystemClockProvider(wallMillis::get, monotonicNanos::get);
        assertThat(provider.isClockSuspect()).isFalse();
        log.info("验证健康墙钟与单调时钟同步推进");
        for (int sample = 1; sample <= 10; sample++) {
            wallMillis.addAndGet(1_000L);
            monotonicNanos.addAndGet(1_000_000_000L);
            assertThat(provider.isClockSuspect()).isFalse();
        }
        assertThat(provider.currentEffectiveNow()).isEqualTo(20L);
    }

    @Test
    void wallClockJumpForwardCannotPolluteTrustedTimeAndRemainsSuspect() {
        AtomicLong wallMillis = new AtomicLong(10_000L);
        AtomicLong monotonicNanos = new AtomicLong(0L);
        SystemClockProvider provider = new SystemClockProvider(wallMillis::get, monotonicNanos::get);
        assertThat(provider.isClockSuspect()).isFalse();
        wallMillis.set(60_000L);
        monotonicNanos.set(1_000_000_000L);
        assertThat(provider.isClockSuspect()).isTrue();
        wallMillis.set(11_000L);
        assertThat(provider.isClockSuspect()).isTrue();
    }
}
