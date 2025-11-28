package io.github.surezzzzzz.sdk.s3.exception.base;

/**
 * S3客户端异常基类
 * @author: Sure.
 * @Date: 2024/12/25
 */
public class S3ClientException extends RuntimeException {
    
    public S3ClientException(String message) {
        super(message);
    }
    
    public S3ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}