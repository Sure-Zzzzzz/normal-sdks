package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Download Failed Exception
 *
 * @author surezzzzzz
 */
public class DownloadFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public DownloadFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DownloadFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
