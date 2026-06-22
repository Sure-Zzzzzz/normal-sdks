package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 列举对象失败异常
 */
public class ListObjectsFailedException extends S3ServerException {

    /**
     * 构造列举对象失败异常
     *
     * @param message 错误信息
     */
    public ListObjectsFailedException(String message) {
        super(ErrorCode.LIST_OBJECTS_FAILED, String.format(ErrorMessage.LIST_OBJECTS_FAILED, message));
    }

    /**
     * 构造列举对象失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public ListObjectsFailedException(String message, Throwable cause) {
        super(ErrorCode.LIST_OBJECTS_FAILED, String.format(ErrorMessage.LIST_OBJECTS_FAILED, message), cause);
    }
}