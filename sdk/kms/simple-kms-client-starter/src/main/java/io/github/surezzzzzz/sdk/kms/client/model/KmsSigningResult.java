package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 面向 {@code TenantSignerPort} 的最小签名结果投影。
 *
 * <p>与 {@link KmsSignature} 分离，避免将完整 HTTP 结果中的逻辑密钥标识泄露到业务端口；
 * 固定算法语义由端口适配器写入，签名字节在出入模型时均复制。</p>
 */
@Value
public class KmsSigningResult {
    Integer version;
    String algorithm;
    byte[] signature;

    /**
     * 在构造时复制签名。
     *
     * @param version   实际签名版本
     * @param algorithm 签名算法
     * @param signature JOSE 签名字节
     */
    @Builder
    public KmsSigningResult(Integer version, String algorithm, byte[] signature) {
        this.version = version;
        this.algorithm = algorithm;
        this.signature = signature == null ? null : signature.clone();
    }

    /**
     * 返回签名副本。
     */
    public byte[] getSignature() {
        return signature == null ? null : signature.clone();
    }
}
