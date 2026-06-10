package io.github.surezzzzzz.sdk.oss.s3.exception.client;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ClientException;

/**
 * 本地文件未找到异常
 */
public class FileNotFoundException extends S3ClientException {

    public FileNotFoundException(String filePath) {
        super(ErrorCode.FILE_NOT_FOUND, String.format(ErrorMessage.FILE_NOT_FOUND, filePath));
    }

    public FileNotFoundException(String filePath, Throwable cause) {
        super(ErrorCode.FILE_NOT_FOUND, String.format(ErrorMessage.FILE_NOT_FOUND, filePath), cause);
    }
}