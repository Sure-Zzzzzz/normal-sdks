package io.github.surezzzzzz.sdk.kms.feign.client.model;

/**
 * KMS 明文响应
 *
 * <p>Feign wire DTO：公开字段直配 Jackson；时间字段为契约原文（UTC 毫秒字符串），
 * 二进制字段为无填充 Base64url 原文，由调用方按需解析。</p>
 *
 * @author surezzzzzz
 */
public class KmsPlaintextResponse {

    /**
     * 明文（无填充 Base64url）。
     */
    public String plaintext;
}
