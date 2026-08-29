package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Delete Failed Exception
 *
 * @author surezzzzzz
 */
public class DeleteFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public DeleteFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DeleteFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
