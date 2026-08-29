package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Upload Failed Exception
 *
 * @author surezzzzzz
 */
public class UploadFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public UploadFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public UploadFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
