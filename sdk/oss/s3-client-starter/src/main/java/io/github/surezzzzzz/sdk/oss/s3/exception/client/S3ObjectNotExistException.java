package io.github.surezzzzzz.sdk.oss.s3.exception.client;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ClientException;

/**
 * S3对象不存在异常
 */
public class S3ObjectNotExistException extends S3ClientException {

    /**
     * 构造默认对象不存在异常
     */
    public S3ObjectNotExistException() {
        super(ErrorCode.OBJECT_NOT_EXIST, ErrorMessage.OBJECT_NOT_EXIST);
    }

    /**
     * 构造对象不存在异常
     *
     * @param message 错误信息
     */
    public S3ObjectNotExistException(String message) {
        super(ErrorCode.OBJECT_NOT_EXIST, message);
    }

    /**
     * 构造对象不存在异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public S3ObjectNotExistException(String message, Throwable cause) {
        super(ErrorCode.OBJECT_NOT_EXIST, message, cause);
    }
}