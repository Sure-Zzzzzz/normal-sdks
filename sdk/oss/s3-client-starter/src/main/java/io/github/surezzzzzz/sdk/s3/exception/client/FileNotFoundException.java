package io.github.surezzzzzz.sdk.s3.exception.client;

import io.github.surezzzzzz.sdk.s3.exception.base.S3ClientException;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/6/7 11:40
 */
public class FileNotFoundException extends S3ClientException {
    
    public FileNotFoundException(String message) {
        super(message);
    }
    
    public FileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
