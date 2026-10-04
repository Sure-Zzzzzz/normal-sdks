package io.github.surezzzzzz.sdk.kms.feign.client.model;

/**
 * KMS 密钥分页响应
 *
 * <p>Feign wire DTO：公开字段直配 Jackson；时间字段为契约原文（UTC 毫秒字符串），
 * 二进制字段为无填充 Base64url 原文，由调用方按需解析。</p>
 *
 * @author surezzzzzz
 */
public class KmsKeyPageResponse {

    /**
     * 当前页密钥。
     */
    public java.util.List<KmsKeyResponse> items;

    /**
     * 页码。
     */
    public Integer page;

    /**
     * 页大小。
     */
    public Integer size;

    /**
     * 总数。
     */
    public Long total;
}
