package io.github.surezzzzzz.sdk.s3.exception.server;

import io.github.surezzzzzz.sdk.s3.exception.base.S3ServerException;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/6/7 11:43
 */
public class UploadObjectFailedException extends S3ServerException {
    
    public UploadObjectFailedException(String message) {
        super(message);
    }
    
    public UploadObjectFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
