package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 创建存储桶失败异常
 */
public class CreateBucketFailedException extends S3ServerException {

    /**
     * 构造创建存储桶失败异常
     *
     * @param message 错误信息
     */
    public CreateBucketFailedException(String message) {
        super(ErrorCode.CREATE_BUCKET_FAILED, String.format(ErrorMessage.CREATE_BUCKET_FAILED, message));
    }

    /**
     * 构造创建存储桶失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public CreateBucketFailedException(String message, Throwable cause) {
        super(ErrorCode.CREATE_BUCKET_FAILED, String.format(ErrorMessage.CREATE_BUCKET_FAILED, message), cause);
    }
}