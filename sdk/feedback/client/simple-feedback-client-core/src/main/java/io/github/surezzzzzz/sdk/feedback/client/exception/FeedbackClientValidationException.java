package io.github.surezzzzzz.sdk.feedback.client.exception;

import io.github.surezzzzzz.sdk.feedback.client.constant.ErrorMessage;

/** 客户端调用参数不符合 Feedback 客户端契约时抛出的不可重试异常。 */
public class FeedbackClientValidationException extends FeedbackClientException {
    private static final long serialVersionUID = 1L;
    private final String parameter;

    public FeedbackClientValidationException(String parameter) {
        super(String.format(ErrorMessage.CLIENT_PARAMETER_INVALID, parameter));
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }
}
