package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 复制对象失败异常
 */
public class CopyObjectFailedException extends S3ServerException {

    public CopyObjectFailedException(String message) {
        super(ErrorCode.COPY_OBJECT_FAILED, String.format(ErrorMessage.COPY_OBJECT_FAILED, message));
    }

    public CopyObjectFailedException(String message, Throwable cause) {
        super(ErrorCode.COPY_OBJECT_FAILED, String.format(ErrorMessage.COPY_OBJECT_FAILED, message), cause);
    }
}