package io.github.surezzzzzz.sdk.kms.feign.client.model;

/**
 * KMS 密钥响应
 *
 * <p>Feign wire DTO：公开字段直配 Jackson；时间字段为契约原文（UTC 毫秒字符串），
 * 二进制字段为无填充 Base64url 原文，由调用方按需解析。</p>
 *
 * @author surezzzzzz
 */
public class KmsKeyResponse {

    /**
     * 密钥引用。
     */
    public String keyRef;

    /**
     * 密钥别名。
     */
    public String keyAlias;

    /**
     * 用途。
     */
    public String purpose;

    /**
     * 算法。
     */
    public String algorithm;

    /**
     * 状态。
     */
    public String state;

    /**
     * 激活版本。
     */
    public Integer activeVersion;

    /**
     * 行版本（乐观并发）。
     */
    public Long rowVersion;

    /**
     * 创建时间（UTC 毫秒字符串）。
     */
    public String createdAt;

    /**
     * 更新时间（UTC 毫秒字符串）。
     */
    public String updatedAt;
}
