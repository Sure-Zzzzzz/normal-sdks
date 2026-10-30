package io.github.surezzzzzz.sdk.license.core.spi;

import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import java.util.Optional;

/**
 * 信任域公钥存储端口：只接受受控安装的资料，用途与版本独立验证。
 *
 * <p>换钥失败保留旧资料；新凭据缺可信钥不自动信任。凭据、吊销与恢复用途不可混用。
 * 待验文件公钥永远不自授权。</p>
 *
 * @author surezzzzzz
 */
public interface TrustStore {

    /** 按 issuer/product/kid/用途查找已信任公钥。 */
    Optional<TrustedPublicKey> findKey(String issuerId, String productId, String kid,
                                       TrustedPublicKey.Purpose purpose);

    /** 安装新信任资料（仅受控渠道调用）。 */
    void install(TrustedPublicKey key);

    /** 当前 trustVersion（用于检测信任变更）。 */
    long currentTrustVersion(String issuerId, String productId, TrustedPublicKey.Purpose purpose);
}
