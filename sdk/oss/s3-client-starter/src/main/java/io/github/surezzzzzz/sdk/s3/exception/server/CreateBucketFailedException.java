package io.github.surezzzzzz.sdk.s3.exception.server;

import io.github.surezzzzzz.sdk.s3.exception.base.S3ServerException;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/5/31 15:18
 */
public class CreateBucketFailedException extends S3ServerException {
    
    public CreateBucketFailedException(String message) {
        super(message);
    }
    
    public CreateBucketFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
