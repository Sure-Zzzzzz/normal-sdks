package io.github.surezzzzzz.sdk.auth.iam.resource.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.ErrorMessage;

/**
 * IAM受控验证响应协议异常。
 *
 * @author surezzzzzz
 */
public class IamResourceVerificationProtocolException extends SimpleIamResourceServerException {

    private static final long serialVersionUID = 1L;

    public IamResourceVerificationProtocolException() {
        super(ErrorCode.VERIFICATION_PROTOCOL_INVALID, ErrorMessage.VERIFICATION_PROTOCOL_INVALID);
    }

    public IamResourceVerificationProtocolException(Throwable cause) {
        super(ErrorCode.VERIFICATION_PROTOCOL_INVALID, ErrorMessage.VERIFICATION_PROTOCOL_INVALID, cause);
    }
}
