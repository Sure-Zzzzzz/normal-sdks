package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 参数校验异常。
 *
 * <p>仅表达安全的通用参数错误，不在消息中回显非法输入。</p>
 *
 * @author surezzzzzz
 */
public class KmsValidationException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的参数校验异常。
     */
    public KmsValidationException() {
        super(ErrorCode.VALIDATION_FAILED, ErrorMessage.VALIDATION_FAILED);
    }
}
