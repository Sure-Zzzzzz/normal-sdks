package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

/**
 * 供 REST 层获取实际签名版本的内部操作端口。
 *
 * @author surezzzzzz
 */
public interface KmsSignatureOperationService {

    /**
     * 执行 ES256 签名并返回实际使用的密钥版本。
     *
     * @param principal 已认证调用主体
     * @param keyRef    逻辑密钥标识
     * @param version   指定版本；为空时在锁定视图内选择活动版本
     * @param input     待签名字节
     * @param requestId 请求标识
     * @return 含实际签名版本的安全结果
     */
    KmsSignatureOperationResult sign(KmsPrincipal principal, String keyRef, Integer version, byte[] input,
                                     String requestId);
}
