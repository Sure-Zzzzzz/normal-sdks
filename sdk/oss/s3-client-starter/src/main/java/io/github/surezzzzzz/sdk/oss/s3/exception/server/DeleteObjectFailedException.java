package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 删除对象失败异常
 */
public class DeleteObjectFailedException extends S3ServerException {

    public DeleteObjectFailedException(String message) {
        super(ErrorCode.DELETE_OBJECT_FAILED, String.format(ErrorMessage.DELETE_OBJECT_FAILED, message));
    }

    public DeleteObjectFailedException(String message, Throwable cause) {
        super(ErrorCode.DELETE_OBJECT_FAILED, String.format(ErrorMessage.DELETE_OBJECT_FAILED, message), cause);
    }
}