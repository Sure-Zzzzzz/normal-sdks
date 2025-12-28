package io.github.surezzzzzz.sdk.sensitive.keyword.exception;

/**
 * NLP Exception
 *
 * @author surezzzzzz
 */
public class NLPException extends SmartKeywordSensitiveException {

    public NLPException(String errorCode, String message) {
        super(errorCode, message);
    }

    public NLPException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
