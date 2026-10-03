package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Event Parse Failed Exception
 *
 * @author surezzzzzz
 */
public class EventParseFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public EventParseFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public EventParseFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
