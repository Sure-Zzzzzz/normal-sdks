package io.github.surezzzzzz.sdk.oss.s3.exception.client;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ClientException;

/**
 * S3对象已存在异常
 */
public class ObjectAlreadyExistsException extends S3ClientException {

    /**
     * 构造默认对象已存在异常
     */
    public ObjectAlreadyExistsException() {
        super(ErrorCode.OBJECT_ALREADY_EXISTS, ErrorMessage.OBJECT_ALREADY_EXISTS);
    }

    /**
     * 构造对象已存在异常
     *
     * @param message 错误信息
     */
    public ObjectAlreadyExistsException(String message) {
        super(ErrorCode.OBJECT_ALREADY_EXISTS, message);
    }

    /**
     * 构造对象已存在异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public ObjectAlreadyExistsException(String message, Throwable cause) {
        super(ErrorCode.OBJECT_ALREADY_EXISTS, message, cause);
    }
}