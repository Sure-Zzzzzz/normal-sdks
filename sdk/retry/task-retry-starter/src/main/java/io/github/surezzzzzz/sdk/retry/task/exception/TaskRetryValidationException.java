package io.github.surezzzzzz.sdk.retry.task.exception;

/**
 * Task Retry 参数校验异常
 *
 * @author surezzzzzz
 */
public class TaskRetryValidationException extends TaskRetryException {

    public TaskRetryValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
