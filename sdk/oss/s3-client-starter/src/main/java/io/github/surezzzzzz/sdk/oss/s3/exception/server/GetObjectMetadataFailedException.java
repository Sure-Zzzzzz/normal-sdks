package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 获取对象元信息失败异常
 */
public class GetObjectMetadataFailedException extends S3ServerException {

    public GetObjectMetadataFailedException(String message) {
        super(ErrorCode.GET_OBJECT_METADATA_FAILED, String.format(ErrorMessage.GET_OBJECT_METADATA_FAILED, message));
    }

    public GetObjectMetadataFailedException(String message, Throwable cause) {
        super(ErrorCode.GET_OBJECT_METADATA_FAILED, String.format(ErrorMessage.GET_OBJECT_METADATA_FAILED, message), cause);
    }
}