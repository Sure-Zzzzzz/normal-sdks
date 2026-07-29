package io.github.surezzzzzz.sdk.kms.client.port;

/**
 * 面向业务调用方的 KMS envelope 加解密端口。
 *
 * <p>解密只接收 KMS 返回的 envelope，调用方无需也不得重新拼接逻辑密钥或版本。</p>
 */
public interface KeyEncryptionPort {
    /**
     * 使用逻辑密钥加密明文字节。
     *
     * @param keyRef    逻辑密钥标识
     * @param plaintext 明文字节
     * @param aad       可选的附加认证数据
     * @return 可由本端口解密的版本化 envelope 字节
     */
    byte[] encrypt(String keyRef, byte[] plaintext, byte[] aad);

    /**
     * 解密 KMS 返回的版本化 envelope。
     *
     * @param envelope KMS 返回的完整 envelope 字节
     * @param aad      可选的附加认证数据，必须与加密时一致
     * @return 原始明文字节
     */
    byte[] decrypt(byte[] envelope, byte[] aad);
}
