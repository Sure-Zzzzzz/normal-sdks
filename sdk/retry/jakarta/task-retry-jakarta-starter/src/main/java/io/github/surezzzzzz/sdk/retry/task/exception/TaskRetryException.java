package io.github.surezzzzzz.sdk.retry.task.exception;

import lombok.Getter;

/**
 * Task Retry 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class TaskRetryException extends RuntimeException {

    private final String errorCode;

    public TaskRetryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TaskRetryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
