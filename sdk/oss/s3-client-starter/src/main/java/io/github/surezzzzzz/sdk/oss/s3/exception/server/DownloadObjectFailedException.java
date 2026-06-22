package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 下载对象失败异常
 */
public class DownloadObjectFailedException extends S3ServerException {

    /**
     * 构造下载对象失败异常
     *
     * @param message 错误信息
     */
    public DownloadObjectFailedException(String message) {
        super(ErrorCode.DOWNLOAD_OBJECT_FAILED, String.format(ErrorMessage.DOWNLOAD_OBJECT_FAILED, message));
    }

    /**
     * 构造下载对象失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public DownloadObjectFailedException(String message, Throwable cause) {
        super(ErrorCode.DOWNLOAD_OBJECT_FAILED, String.format(ErrorMessage.DOWNLOAD_OBJECT_FAILED, message), cause);
    }
}