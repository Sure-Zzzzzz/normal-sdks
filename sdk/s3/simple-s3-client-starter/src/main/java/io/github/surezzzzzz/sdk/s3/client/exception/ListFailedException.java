package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * List Failed Exception
 *
 * @author surezzzzzz
 */
public class ListFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public ListFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ListFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
