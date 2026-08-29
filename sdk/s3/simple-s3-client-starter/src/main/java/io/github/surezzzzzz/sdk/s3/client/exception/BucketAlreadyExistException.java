package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Bucket Already Exist Exception
 *
 * @author surezzzzzz
 */
public class BucketAlreadyExistException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public BucketAlreadyExistException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BucketAlreadyExistException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
