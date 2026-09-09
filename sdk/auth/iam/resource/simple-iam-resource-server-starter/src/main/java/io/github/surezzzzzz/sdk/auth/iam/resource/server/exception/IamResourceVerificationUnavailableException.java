package io.github.surezzzzzz.sdk.auth.iam.resource.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.ErrorMessage;

/**
 * IAM受控验证服务不可用异常。
 *
 * @author surezzzzzz
 */
public class IamResourceVerificationUnavailableException extends SimpleIamResourceServerException {

    private static final long serialVersionUID = 1L;

    public IamResourceVerificationUnavailableException() {
        super(ErrorCode.VERIFICATION_UNAVAILABLE, ErrorMessage.VERIFICATION_UNAVAILABLE);
    }

    public IamResourceVerificationUnavailableException(Throwable cause) {
        super(ErrorCode.VERIFICATION_UNAVAILABLE, ErrorMessage.VERIFICATION_UNAVAILABLE, cause);
    }
}
