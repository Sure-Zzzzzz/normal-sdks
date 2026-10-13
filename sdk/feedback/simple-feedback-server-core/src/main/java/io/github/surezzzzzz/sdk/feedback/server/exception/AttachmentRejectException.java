package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 附件校验异常（400/413/415 按错误码细分）
 *
 * @author surezzzzzz
 */
public class AttachmentRejectException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public AttachmentRejectException(String errorCode, String message) {
        super(errorCode, message);
    }
}
