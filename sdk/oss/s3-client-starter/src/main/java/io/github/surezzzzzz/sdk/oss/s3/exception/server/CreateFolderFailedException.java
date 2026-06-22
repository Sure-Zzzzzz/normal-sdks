package io.github.surezzzzzz.sdk.oss.s3.exception.server;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;

/**
 * 创建文件夹失败异常
 */
public class CreateFolderFailedException extends S3ServerException {

    /**
     * 构造创建文件夹失败异常
     *
     * @param message 错误信息
     */
    public CreateFolderFailedException(String message) {
        super(ErrorCode.CREATE_FOLDER_FAILED, String.format(ErrorMessage.CREATE_FOLDER_FAILED, message));
    }

    /**
     * 构造创建文件夹失败异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public CreateFolderFailedException(String message, Throwable cause) {
        super(ErrorCode.CREATE_FOLDER_FAILED, String.format(ErrorMessage.CREATE_FOLDER_FAILED, message), cause);
    }
}