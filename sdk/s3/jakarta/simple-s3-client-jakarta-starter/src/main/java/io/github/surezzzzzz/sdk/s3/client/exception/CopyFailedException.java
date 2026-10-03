package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Copy Failed Exception
 *
 * @author surezzzzzz
 */
public class CopyFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public CopyFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CopyFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
