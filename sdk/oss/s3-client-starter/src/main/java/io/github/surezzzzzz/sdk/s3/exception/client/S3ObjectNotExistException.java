package io.github.surezzzzzz.sdk.s3.exception.client;

import io.github.surezzzzzz.sdk.s3.exception.base.S3ClientException;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/5/31 14:27
 */
public class S3ObjectNotExistException extends S3ClientException {
    public S3ObjectNotExistException(String message) {
        super(message);
    }
}
