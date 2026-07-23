package io.github.surezzzzzz.sdk.kms.core.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

/**
 * KMS 授权服务。
 *
 * <p>实现必须同时检查 scope 与未过期的精确 allow-only policy；tenant 只允许从
 * {@link KmsPrincipal} 派生，不能由请求体、查询参数或 HTTP Header 覆盖。</p>
 *
 * @author surezzzzzz
 */
public interface KmsAuthorizationService {

    /**
     * 授权主体访问指定密钥版本和操作。
     *
     * @param principal 已认证主体
     * @param keyRef    逻辑密钥标识
     * @param version   目标版本号
     * @param operation 目标操作
     * @param requestId 用于安全审计的请求标识
     */
    void authorize(KmsPrincipal principal, String keyRef, int version, KmsOperation operation,
                   String requestId);
}
