package io.github.surezzzzzz.sdk.oss.s3.exception.client;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ClientException;

/**
 * S3对象已存在异常
 */
public class ObjectAlreadyExistsException extends S3ClientException {

    public ObjectAlreadyExistsException() {
        super(ErrorCode.OBJECT_ALREADY_EXISTS, ErrorMessage.OBJECT_ALREADY_EXISTS);
    }

    public ObjectAlreadyExistsException(String message) {
        super(ErrorCode.OBJECT_ALREADY_EXISTS, message);
    }

    public ObjectAlreadyExistsException(String message, Throwable cause) {
        super(ErrorCode.OBJECT_ALREADY_EXISTS, message, cause);
    }
}