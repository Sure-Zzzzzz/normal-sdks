package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

/**
 * KMS 管理读取授权端口。
 *
 * @author surezzzzzz
 */
public interface KmsManagementReadAuthorizer {

    /**
     * 校验主体拥有管理读取权限。
     *
     * @param principal 已认证主体
     * @param requestId 请求标识
     */
    void authorize(KmsPrincipal principal, String requestId);
}
