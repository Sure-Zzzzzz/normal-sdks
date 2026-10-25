package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 反馈服务配置异常（启动期拒绝不完整装配）。
 *
 * @author surezzzzzz
 */
public class FeedbackConfigurationException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public FeedbackConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FeedbackConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
