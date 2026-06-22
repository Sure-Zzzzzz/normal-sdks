package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 上传分段失败异常
 */
public class UploadPartFailedException extends S3ServerException {

    /**
     * 构造上传分段失败异常
     *
     * @param message 错误信息
     */
    public UploadPartFailedException(String message) {
        super(ErrorCode.UPLOAD_PART_FAILED, String.format(ErrorMessage.UPLOAD_PART_FAILED, message));
    }

    /**
     * 构造上传分段失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public UploadPartFailedException(String message, Throwable cause) {
        super(ErrorCode.UPLOAD_PART_FAILED, String.format(ErrorMessage.UPLOAD_PART_FAILED, message), cause);
    }
}