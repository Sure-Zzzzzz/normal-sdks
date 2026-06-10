package io.github.surezzzzzz.sdk.oss.s3.exception.base;

import lombok.Getter;

/**
 * S3服务端异常基类
 */
@Getter
public class S3ServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public S3ServerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public S3ServerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}