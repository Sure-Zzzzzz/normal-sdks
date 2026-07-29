package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * KMS 指定版本的可发布公钥。
 *
 * <p>仅表示可向验签方发布的公开材料；构造器与访问器均复制字节数组，避免可变数组破坏不可变结果语义。</p>
 */
@Value
public class KmsPublicKey {
    String keyRef;
    Integer version;
    String algorithm;
    String state;
    byte[] publicKey;

    /**
     * 在构造时复制公钥。
     *
     * @param keyRef    逻辑密钥标识
     * @param version   密钥版本
     * @param algorithm 密码算法
     * @param state     密钥版本状态
     * @param publicKey 公钥字节
     */
    @Builder
    public KmsPublicKey(String keyRef, Integer version, String algorithm, String state, byte[] publicKey) {
        this.keyRef = keyRef;
        this.version = version;
        this.algorithm = algorithm;
        this.state = state;
        this.publicKey = publicKey == null ? null : publicKey.clone();
    }

    /**
     * 返回公钥副本。
     */
    public byte[] getPublicKey() {
        return publicKey == null ? null : publicKey.clone();
    }
}
