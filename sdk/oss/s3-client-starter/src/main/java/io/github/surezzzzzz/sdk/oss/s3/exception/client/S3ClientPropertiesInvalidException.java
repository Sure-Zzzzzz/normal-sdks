package io.github.surezzzzzz.sdk.oss.s3.exception.client;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ClientException;

/**
 * S3Client 配置参数非法异常
 */
public class S3ClientPropertiesInvalidException extends S3ClientException {

    /**
     * 构造配置参数非法异常
     *
     * @param message 错误信息
     */
    public S3ClientPropertiesInvalidException(String message) {
        super(ErrorCode.S3_CLIENT_PROPERTIES_INVALID, message);
    }

    /**
     * 构造配置参数非法异常
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public S3ClientPropertiesInvalidException(String message, Throwable cause) {
        super(ErrorCode.S3_CLIENT_PROPERTIES_INVALID, message, cause);
    }
}
