package io.github.surezzzzzz.sdk.s3.exception.base;

/**
 * S3服务端异常基类
 * @author: Sure.
 * @Date: 2024/12/25
 */
public class S3ServerException extends RuntimeException {
    
    public S3ServerException(String message) {
        super(message);
    }
    
    public S3ServerException(String message, Throwable cause) {
        super(message, cause);
    }
}