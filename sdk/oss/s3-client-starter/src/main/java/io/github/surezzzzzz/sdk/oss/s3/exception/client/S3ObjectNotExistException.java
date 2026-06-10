package io.github.surezzzzzz.sdk.oss.s3.exception.client;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ClientException;

/**
 * S3对象不存在异常
 */
public class S3ObjectNotExistException extends S3ClientException {

    public S3ObjectNotExistException() {
        super(ErrorCode.OBJECT_NOT_EXIST, ErrorMessage.OBJECT_NOT_EXIST);
    }

    public S3ObjectNotExistException(String message) {
        super(ErrorCode.OBJECT_NOT_EXIST, message);
    }

    public S3ObjectNotExistException(String message, Throwable cause) {
        super(ErrorCode.OBJECT_NOT_EXIST, message, cause);
    }
}