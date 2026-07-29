package io.github.surezzzzzz.sdk.kms.client.port;

import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;

import java.util.List;

/**
 * 面向业务调用方的租户公钥读取端口。
 */
public interface TenantPublicKeyPort {
    /**
     * 读取指定或当前活动版本的可发布公钥。
     *
     * @param keyRef  逻辑密钥标识
     * @param version 指定版本；为空时由 KMS 选择当前活动版本
     * @return 版本级可发布公钥
     */
    KmsPublicKey read(String keyRef, Integer version);

    /**
     * 读取逻辑密钥的全部可发布公钥。
     *
     * @param keyRef 逻辑密钥标识
     * @return 不可变公钥集合
     */
    List<KmsPublicKey> list(String keyRef);
}
