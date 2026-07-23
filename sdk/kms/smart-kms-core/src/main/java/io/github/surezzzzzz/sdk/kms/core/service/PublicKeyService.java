package io.github.surezzzzzz.sdk.kms.core.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;

import java.util.List;

/**
 * 公钥发布服务。
 *
 * <p>仅返回 ES256 的 {@code ACTIVE}/{@code RETIRED} 公钥版本；待销毁和已销毁版本绝不发布。
 * 单条读取与集合读取都必须同时经过 {@code READ_PUBLIC_KEY} scope 和精确策略校验。</p>
 *
 * @author surezzzzzz
 */
public interface PublicKeyService {

    /**
     * 读取指定可分发公钥版本。
     */
    KmsPublicKey read(KmsPrincipal principal, String keyRef, Integer version, String requestId);

    /**
     * 按版本升序读取逻辑密钥当前完整的可分发公钥集合。
     */
    List<KmsPublicKey> list(KmsPrincipal principal, String keyRef, String requestId);
}
