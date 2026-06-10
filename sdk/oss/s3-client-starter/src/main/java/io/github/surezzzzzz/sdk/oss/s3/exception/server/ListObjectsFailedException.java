package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 列举对象失败异常
 */
public class ListObjectsFailedException extends S3ServerException {

    public ListObjectsFailedException(String message) {
        super(ErrorCode.LIST_OBJECTS_FAILED, String.format(ErrorMessage.LIST_OBJECTS_FAILED, message));
    }

    public ListObjectsFailedException(String message, Throwable cause) {
        super(ErrorCode.LIST_OBJECTS_FAILED, String.format(ErrorMessage.LIST_OBJECTS_FAILED, message), cause);
    }
}