package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 上传对象失败异常
 */
public class UploadObjectFailedException extends S3ServerException {

    /**
     * 构造上传对象失败异常
     *
     * @param message 错误信息
     */
    public UploadObjectFailedException(String message) {
        super(ErrorCode.UPLOAD_OBJECT_FAILED, String.format(ErrorMessage.UPLOAD_OBJECT_FAILED, message));
    }

    /**
     * 构造上传对象失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public UploadObjectFailedException(String message, Throwable cause) {
        super(ErrorCode.UPLOAD_OBJECT_FAILED, String.format(ErrorMessage.UPLOAD_OBJECT_FAILED, message), cause);
    }
}