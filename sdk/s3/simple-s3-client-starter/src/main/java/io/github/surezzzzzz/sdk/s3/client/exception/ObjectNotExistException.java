package io.github.surezzzzzz.sdk.s3.client.exception;

/**
 * Object Not Exist Exception
 *
 * @author surezzzzzz
 */
public class ObjectNotExistException extends S3ClientException {

    private static final long serialVersionUID = 1L;

    public ObjectNotExistException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ObjectNotExistException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
