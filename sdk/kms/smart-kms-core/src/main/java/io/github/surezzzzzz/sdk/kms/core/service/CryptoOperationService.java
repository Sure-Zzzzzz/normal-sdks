package io.github.surezzzzzz.sdk.kms.core.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

/**
 * KMS 密码学操作服务。
 *
 * <p>所有方法以已认证主体和 requestId 作为输入，tenant 只从主体派生。实现必须先完成状态、scope、
 * 精确 policy 和审计约束，再调用密码学引擎；不得将失败原因或密码学材料回传给调用方。</p>
 *
 * @author surezzzzzz
 */
public interface CryptoOperationService {

    /**
     * 使用指定或当前活动的 ES256 版本签名不透明输入字节。
     */
    byte[] sign(KmsPrincipal principal, String keyRef, Integer version, byte[] input, String requestId);

    /**
     * 使用指定或当前活动的 ES256 公钥版本验证 JOSE 签名。
     */
    boolean verify(KmsPrincipal principal, String keyRef, Integer version, byte[] input,
                   byte[] signature, String requestId);

    /**
     * 使用逻辑密钥当前活动的 AES-256-GCM 版本加密明文。
     */
    byte[] encrypt(KmsPrincipal principal, String keyRef, byte[] plaintext, byte[] externalAad,
                   String requestId);

    /**
     * 从 SKMS 封装中提取密钥版本并解密，调用方不得覆盖该版本。
     */
    byte[] decrypt(KmsPrincipal principal, byte[] envelope, byte[] externalAad, String requestId);
}
