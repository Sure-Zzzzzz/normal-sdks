package io.github.surezzzzzz.sdk.naturallanguage.parser.exception;

import lombok.Getter;

/**
 * Natural Language Parser 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class NLParserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    public NLParserException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NLParserException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
