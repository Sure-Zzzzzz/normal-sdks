package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Access Denied Exception
 *
 * @author surezzzzzz
 */
public class AccessDeniedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AccessDeniedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
