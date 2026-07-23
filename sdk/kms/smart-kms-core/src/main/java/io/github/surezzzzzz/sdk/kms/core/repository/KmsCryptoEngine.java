package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;

/**
 * 密码学执行端口。
 *
 * <p>这是 KMS 可信边界内部的窄适配端口，材料参数不得进入 DTO、日志、审计、事件或异常信息。
 * server 层以 JCA 实现该端口；core 不依赖 Provider、{@code Signature} 或 {@code Cipher}。</p>
 *
 * @author surezzzzzz
 */
public interface KmsCryptoEngine {

    /**
     * 使用私钥材料执行签名并返回 JOSE ES256 签名。
     */
    byte[] sign(KmsAlgorithm algorithm, byte[] privateMaterial, byte[] input);

    /**
     * 使用公钥材料验证 JOSE ES256 签名。
     */
    boolean verify(KmsAlgorithm algorithm, byte[] publicMaterial, byte[] input, byte[] signature);

    /**
     * 使用对称材料和已构造 AAD 执行 AES-256-GCM 加密。
     */
    byte[] encrypt(KmsAlgorithm algorithm, byte[] symmetricMaterial, byte[] plaintext, byte[] aad);

    /**
     * 使用对称材料和已构造 AAD 执行 AES-256-GCM 解密。
     */
    byte[] decrypt(KmsAlgorithm algorithm, byte[] symmetricMaterial, byte[] ciphertext, byte[] aad);
}
