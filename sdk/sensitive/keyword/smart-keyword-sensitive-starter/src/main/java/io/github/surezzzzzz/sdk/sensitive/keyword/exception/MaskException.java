package io.github.surezzzzzz.sdk.sensitive.keyword.exception;

/**
 * Mask Exception
 *
 * @author surezzzzzz
 */
public class MaskException extends SmartKeywordSensitiveException {

    public MaskException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MaskException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
