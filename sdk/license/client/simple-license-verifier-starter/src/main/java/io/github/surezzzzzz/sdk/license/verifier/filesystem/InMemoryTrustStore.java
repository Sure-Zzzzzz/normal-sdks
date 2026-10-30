package io.github.surezzzzzz.sdk.license.verifier.filesystem;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import java.util.Arrays;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存信任资料存储（默认实现；生产可替换为持久化实现）。
 *
 * <p>kid 全局唯一，信任域、用途和公钥不可改绑；查找仍核对 issuer/product/用途。
 * 同一资料可提升信任版本，换钥必须使用新 kid。安装冲突原子拒绝并保留旧资料。</p>
 *
 * @author surezzzzzz
 */
public class InMemoryTrustStore implements TrustStore {

    private final Map<String, TrustedPublicKey> keys = new ConcurrentHashMap<String, TrustedPublicKey>();

    @Override
    public Optional<TrustedPublicKey> findKey(String issuerId, String productId, String kid,
                                              TrustedPublicKey.Purpose purpose) {
        return Optional.ofNullable(keys.get(kid))
                .filter(key -> key.getIssuerId().equals(issuerId)
                        && key.getProductId().equals(productId)
                        && key.getPurpose() == purpose);
    }

    @Override
    public void install(TrustedPublicKey key) {
        // 以全局 kid 原子检查，不能用提升版本或切换用途绕过不可改绑约束。
        keys.compute(key.getKid(), (ignored, existing) -> {
            if (existing == null) {
                return key;
            }
            if (!existing.getIssuerId().equals(key.getIssuerId())
                    || !existing.getProductId().equals(key.getProductId())
                    || existing.getPurpose() != key.getPurpose()
                    || !Arrays.equals(existing.getSpki(), key.getSpki())) {
                throw new IllegalArgumentException(ErrorMessage.CRYPTO_SIGNATURE_INVALID);
            }
            return key.getTrustVersion() > existing.getTrustVersion() ? key : existing;
        });
    }

    @Override
    public long currentTrustVersion(String issuerId, String productId, TrustedPublicKey.Purpose purpose) {
        return keys.values().stream()
                .filter(key -> key.getIssuerId().equals(issuerId)
                        && key.getProductId().equals(productId)
                        && key.getPurpose() == purpose)
                .mapToLong(TrustedPublicKey::getTrustVersion)
                .max().orElse(0L);
    }

}
