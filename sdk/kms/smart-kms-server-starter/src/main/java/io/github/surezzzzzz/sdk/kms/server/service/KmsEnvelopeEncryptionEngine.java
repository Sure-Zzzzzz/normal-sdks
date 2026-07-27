package io.github.surezzzzzz.sdk.kms.server.service;

/**
 * SKMS 封装加密内部端口。
 *
 * @author surezzzzzz
 */
public interface KmsEnvelopeEncryptionEngine {

    /**
     * 生成随机 IV，并使用包含最终 IV 的 SKMS 头构造 GCM AAD 后加密。
     *
     * @param symmetricMaterial AES-256-GCM 对称材料
     * @param keyRef            逻辑密钥标识
     * @param keyVersion        精确密钥版本
     * @param plaintext         待加密明文
     * @param externalAad       调用方附加 AAD
     * @return 完整 SKMS v1 二进制封装
     */
    byte[] encryptEnvelope(byte[] symmetricMaterial, String keyRef, int keyVersion, byte[] plaintext,
                           byte[] externalAad);
}
