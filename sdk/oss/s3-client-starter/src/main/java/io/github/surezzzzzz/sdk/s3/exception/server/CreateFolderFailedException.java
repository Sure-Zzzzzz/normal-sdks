package io.github.surezzzzzz.sdk.s3.exception.server;

import io.github.surezzzzzz.sdk.s3.exception.base.S3ServerException;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/5/31 15:18
 */
public class CreateFolderFailedException extends S3ServerException {
    
    public CreateFolderFailedException(String message) {
        super(message);
    }
    
    public CreateFolderFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
