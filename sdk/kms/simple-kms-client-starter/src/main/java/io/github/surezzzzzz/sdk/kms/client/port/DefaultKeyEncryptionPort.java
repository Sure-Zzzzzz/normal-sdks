package io.github.surezzzzzz.sdk.kms.client.port;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClient;

/**
 * 基于完整 KMS Client 的默认 envelope 加解密端口。
 *
 * @author surezzzzzz
 */
public class DefaultKeyEncryptionPort implements KeyEncryptionPort {

    private final KmsClient kmsClient;

    /**
     * 创建默认 KMS 加解密端口。
     *
     * @param kmsClient 完整 KMS Client
     */
    public DefaultKeyEncryptionPort(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @Override
    public byte[] encrypt(String keyRef, byte[] plaintext, byte[] aad) {
        return kmsClient.encrypt(keyRef, plaintext, aad);
    }

    @Override
    public byte[] decrypt(byte[] envelope, byte[] aad) {
        return kmsClient.decrypt(envelope, aad);
    }
}
