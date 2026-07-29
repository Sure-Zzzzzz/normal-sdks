package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * KMS 签名 HTTP 响应的完整结果。
 *
 * <p>保留服务端返回的逻辑密钥标识、实际版本与 JOSE 签名，供需要完整 HTTP 契约信息的调用方使用；
 * 签名字节在出入模型时均复制，防止外部修改结果内容。</p>
 */
@Value
public class KmsSignature {
    String keyRef;
    Integer version;
    byte[] signature;

    /**
     * 在构造时复制签名。
     *
     * @param keyRef    逻辑密钥标识
     * @param version   实际签名版本
     * @param signature JOSE 签名字节
     */
    @Builder
    public KmsSignature(String keyRef, Integer version, byte[] signature) {
        this.keyRef = keyRef;
        this.version = version;
        this.signature = signature == null ? null : signature.clone();
    }

    /**
     * 返回签名副本。
     */
    public byte[] getSignature() {
        return signature == null ? null : signature.clone();
    }
}
