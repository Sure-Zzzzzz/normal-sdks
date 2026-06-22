package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 完成分段上传失败异常
 */
public class CompleteMultipartUploadFailedException extends S3ServerException {

    /**
     * 构造完成分段上传失败异常
     *
     * @param message 错误信息
     */
    public CompleteMultipartUploadFailedException(String message) {
        super(ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, String.format(ErrorMessage.COMPLETE_MULTIPART_UPLOAD_FAILED, message));
    }

    /**
     * 构造完成分段上传失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public CompleteMultipartUploadFailedException(String message, Throwable cause) {
        super(ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, String.format(ErrorMessage.COMPLETE_MULTIPART_UPLOAD_FAILED, message), cause);
    }
}