package io.github.surezzzzzz.sdk.kms.feign.client.model;

/**
 * KMS 公钥响应
 *
 * <p>Feign wire DTO：公开字段直配 Jackson；时间字段为契约原文（UTC 毫秒字符串），
 * 二进制字段为无填充 Base64url 原文，由调用方按需解析。</p>
 *
 * @author surezzzzzz
 */
public class KmsPublicKeyResponse {

    /**
     * 密钥引用。
     */
    public String keyRef;

    /**
     * 版本。
     */
    public Integer version;

    /**
     * 算法。
     */
    public String algorithm;

    /**
     * 版本状态。
     */
    public String state;

    /**
     * 公钥（无填充 Base64url）。
     */
    public String publicKey;
}
