package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;

import java.util.List;

/**
 * 已验证人员的本人公钥查询端口，独立于调用方使用策略授权。
 *
 * @author surezzzzzz
 */
public interface KmsMyPublicKeyService {

    /**
     * 在本人归属内读取全部可分发公钥，仍要求人员证明和精确 API 权限。
     *
     * @param context 可信认证器产生的请求上下文
     * @param keyRef  逻辑密钥标识
     * @return 按版本升序排列的公钥
     */
    List<KmsPublicKey> list(KmsRequestContext context, String keyRef);
}
