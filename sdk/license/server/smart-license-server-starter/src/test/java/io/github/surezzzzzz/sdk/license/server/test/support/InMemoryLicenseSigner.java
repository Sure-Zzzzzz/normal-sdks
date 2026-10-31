package io.github.surezzzzzz.sdk.license.server.test.support;

import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMS LicenseSigner 适配（宿主装配件；测试/本地可用内存实现）。
 *
 * <p>生产实现包装官方 KMS 客户端：创建钥必须由 License 自身 AKSK 身份执行（owner
 * aksk:{clientId}），签名按精确 keyRef/keyVersion，本地以登记公钥验回。签名档案登记
 * 与轮换流程属治理层，不在本类。此处提供内存实现供 Core/Server 测试与非生产向量。</p>
 *
 * @author surezzzzzz
 */
public final class InMemoryLicenseSigner implements LicenseSigner {

    private final Map<String, byte[]> spkiByProfile = new LinkedHashMap<String, byte[]>();
    private final Map<String, java.security.PrivateKey> privateKeyByProfile =
            new LinkedHashMap<String, java.security.PrivateKey>();
    private final Map<String, TrustedPublicKey> publicKeyByProfile =
            new LinkedHashMap<String, TrustedPublicKey>();

    /** 登记档案（测试向量：随机钥对）。 */
    public void register(String signingProfileId, java.security.KeyPair keyPair, String issuerId,
                         String productId, String kid) {
        privateKeyByProfile.put(signingProfileId, keyPair.getPrivate());
        byte[] spki = keyPair.getPublic().getEncoded();
        spkiByProfile.put(signingProfileId, spki);
        publicKeyByProfile.put(signingProfileId, TrustedPublicKey.of(issuerId, productId, kid,
                TrustedPublicKey.Purpose.CREDENTIAL, 1L, spki));
    }

    @Override
    public byte[] sign(String signingProfileId, SigningInput input) {
        java.security.PrivateKey key = privateKeyByProfile.get(signingProfileId);
        if (key == null) {
            throw new IllegalStateException("签名档案未登记：" + signingProfileId);
        }
        return io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives.signForTesting(
                input.getBytes(), key);
    }

    /** 登记的信任公钥（测试辅助查询，非 SPI 契约：生产验签用档案持久化快照）。 */
    public TrustedPublicKey registeredPublicKey(String signingProfileId) {
        TrustedPublicKey key = publicKeyByProfile.get(signingProfileId);
        if (key == null) {
            throw new IllegalStateException("签名档案未登记：" + signingProfileId);
        }
        return key;
    }
}
