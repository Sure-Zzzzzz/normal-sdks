package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Get Metadata Failed Exception
 *
 * @author surezzzzzz
 */
public class GetMetadataFailedException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public GetMetadataFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public GetMetadataFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
