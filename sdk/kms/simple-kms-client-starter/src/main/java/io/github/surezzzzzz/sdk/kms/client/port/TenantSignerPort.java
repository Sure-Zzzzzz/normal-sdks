package io.github.surezzzzzz.sdk.kms.client.port;

import io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult;

/**
 * 面向业务调用方的最小租户签名端口。
 *
 * <p>不暴露完整 HTTP 签名结果中的逻辑密钥标识，也不负责重试或幂等处理。</p>
 */
public interface TenantSignerPort {
    /**
     * 使用指定或当前活动版本对输入字节签名。
     *
     * @param keyRef       逻辑密钥标识
     * @param version      指定版本；为空时由 KMS 选择当前活动版本
     * @param signingInput 待签名的原始字节，不约定文本编码
     * @return 仅包含实际版本、算法和签名字节的端口层结果
     */
    KmsSigningResult sign(String keyRef, Integer version, byte[] signingInput);
}
