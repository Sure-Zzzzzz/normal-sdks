package io.github.surezzzzzz.sdk.kms.core.exception;

import io.github.surezzzzzz.sdk.kms.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kms.core.constant.ErrorMessage;

/**
 * KMS 生命周期或乐观锁状态冲突异常。
 *
 * @author surezzzzzz
 */
public class KmsStateConflictException extends SmartKmsException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建固定错误码和安全消息的状态冲突异常。
     */
    public KmsStateConflictException() {
        super(ErrorCode.STATE_CONFLICT, ErrorMessage.STATE_CONFLICT);
    }
}
