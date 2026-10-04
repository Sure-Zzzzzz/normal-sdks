package io.github.surezzzzzz.sdk.kms.feign.client.model;

/**
 * KMS 策略响应
 *
 * <p>Feign wire DTO：公开字段直配 Jackson；时间字段为契约原文（UTC 毫秒字符串），
 * 二进制字段为无填充 Base64url 原文，由调用方按需解析。</p>
 *
 * @author surezzzzzz
 */
public class KmsPolicyResponse {

    /**
     * 策略标识。
     */
    public String policyId;

    /**
     * 密钥引用。
     */
    public String keyRef;

    /**
     * 被授权主体。
     */
    public String principalId;

    /**
     * 限定版本（null 表示全部版本）。
     */
    public Integer keyVersion;

    /**
     * 操作。
     */
    public String operation;

    /**
     * 过期时间（UTC 毫秒字符串，null 表示不过期）。
     */
    public String expiresAt;

    /**
     * 行版本（乐观并发）。
     */
    public Long rowVersion;
}
