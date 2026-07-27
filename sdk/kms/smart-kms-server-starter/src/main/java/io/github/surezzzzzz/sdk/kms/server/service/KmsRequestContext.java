package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Getter;

/**
 * 已认证 KMS 请求上下文。
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsRequestContext {

    /**
     * 已认证主体。
     */
    private final KmsPrincipal principal;
    /**
     * 请求关联标识。
     */
    private final String requestId;

    /**
     * 创建已认证请求上下文。
     *
     * @param principal 已认证主体
     * @param requestId 请求关联标识
     */
    public KmsRequestContext(KmsPrincipal principal, String requestId) {
        if (principal == null) {
            throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
        }
        this.principal = principal;
        this.requestId = KmsValidationHelper.requireRequestId(requestId);
    }
}
