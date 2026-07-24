package io.github.surezzzzzz.sdk.kms.core.model;

import lombok.Getter;

import java.util.Arrays;

/**
 * SKMS v1 密文封装。
 *
 * <p>该模型仅表达封装后的密文字节；加解密、随机 IV 生成和 Base64url 传输编码由
 * server 层负责。二进制字段始终通过防御性拷贝隔离，避免调用方在构建后篡改封装内容。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsEnvelope {

    /**
     * KMS 生成的逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 用于解密的无符号密钥版本号。
     */
    private final long keyVersion;
    /**
     * AES-GCM 初始化向量。
     */
    private final byte[] iv;
    /**
     * AES-GCM 密文与认证标签的拼接结果。
     */
    private final byte[] ciphertextAndTag;

    /**
     * 创建密文封装。
     *
     * <p>格式有效性由 {@code KmsEnvelopeHelper} 在序列化或解析时统一校验，模型本身不泄露
     * 具体失败原因。</p>
     *
     * @param keyRef           逻辑密钥标识
     * @param keyVersion       密钥版本号
     * @param iv               AES-GCM 初始化向量
     * @param ciphertextAndTag 密文与认证标签
     */
    public KmsEnvelope(String keyRef, long keyVersion, byte[] iv, byte[] ciphertextAndTag) {
        this.keyRef = keyRef;
        this.keyVersion = keyVersion;
        this.iv = copy(iv);
        this.ciphertextAndTag = copy(ciphertextAndTag);
    }

    /**
     * 复制二进制值，避免可变数组跨越领域边界。
     */
    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    /**
     * 获取初始化向量的副本。
     *
     * @return 初始化向量副本；原值为空时返回 {@code null}
     */
    public byte[] getIv() {
        return copy(iv);
    }

    /**
     * 获取密文与认证标签的副本。
     *
     * @return 密文与认证标签副本；原值为空时返回 {@code null}
     */
    public byte[] getCiphertextAndTag() {
        return copy(ciphertextAndTag);
    }
}
