package io.github.surezzzzzz.sdk.naturallanguage.parser.exception;

/**
 * 关键字冲突异常（启动时，配置错误）
 *
 * @author surezzzzzz
 */
public class NLKeywordConflictException extends NLParserException {

    public NLKeywordConflictException(String errorCode, String message) {
        super(errorCode, message);
    }

    public NLKeywordConflictException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
