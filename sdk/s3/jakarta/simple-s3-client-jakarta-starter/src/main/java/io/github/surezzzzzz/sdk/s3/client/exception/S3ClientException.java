package io.github.surezzzzzz.sdk.s3.client.exception;

import lombok.Getter;

/**
 * S3 Client Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class S3ClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public S3ClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public S3ClientException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
