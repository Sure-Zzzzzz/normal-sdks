package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 幂等键冲突异常（409，同键异摘要）
 *
 * @author surezzzzzz
 */
public class IdempotencyConflictException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
