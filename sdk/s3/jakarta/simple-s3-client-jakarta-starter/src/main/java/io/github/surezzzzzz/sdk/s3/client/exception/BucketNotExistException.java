package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Bucket Not Exist Exception
 *
 * @author surezzzzzz
 */
public class BucketNotExistException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public BucketNotExistException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BucketNotExistException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
