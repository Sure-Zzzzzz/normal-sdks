package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;

/**
 * 默认 KMS 管理读取授权器。
 *
 * @author surezzzzzz
 */
public class DefaultKmsManagementReadAuthorizer implements KmsManagementReadAuthorizer {

    /**
     * 校验管理 scope 和请求标识。
     */
    @Override
    public void authorize(KmsPrincipal principal, String requestId) {
        if (principal == null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireRequestId(requestId);
        if (!principal.hasScope(SmartKmsServerConstant.SCOPE_MANAGE)) {
            throw new KmsAuthorizationException();
        }
    }
}
