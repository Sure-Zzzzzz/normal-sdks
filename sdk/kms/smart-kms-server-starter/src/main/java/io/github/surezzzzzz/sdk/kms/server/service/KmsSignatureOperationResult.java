package io.github.surezzzzzz.sdk.kms.server.service;

import java.util.Arrays;

/**
 * KMS 签名操作的安全响应结果。
 *
 * @author surezzzzzz
 */
public final class KmsSignatureOperationResult {

    /**
     * 实际执行签名的密钥版本。
     */
    private final int version;
    /**
     * ES256 JOSE 签名字节。
     */
    private final byte[] signature;

    /**
     * 创建签名操作结果。
     *
     * @param version   实际执行签名的密钥版本
     * @param signature ES256 JOSE 签名字节
     */
    public KmsSignatureOperationResult(int version, byte[] signature) {
        this.version = version;
        this.signature = signature == null ? null : Arrays.copyOf(signature, signature.length);
    }

    /**
     * 获取实际执行签名的密钥版本。
     *
     * @return 实际执行签名的密钥版本
     */
    public int getVersion() {
        return version;
    }

    /**
     * 获取 ES256 JOSE 签名字节副本。
     *
     * @return ES256 JOSE 签名字节副本
     */
    public byte[] getSignature() {
        return signature == null ? null : Arrays.copyOf(signature, signature.length);
    }
}
