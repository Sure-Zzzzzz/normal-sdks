package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 密码学安全异常。
 *
 * <p>统一归并密文格式、认证、材料和 Provider 层失败，不携带可用于推断内部状态的原因链。</p>
 *
 * @author surezzzzzz
 */
public class KmsCryptoException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的密码学安全异常。
     */
    public KmsCryptoException() {
        super(ErrorCode.CRYPTOGRAPHIC_OPERATION_FAILED, ErrorMessage.CRYPTOGRAPHIC_OPERATION_FAILED);
    }
}
