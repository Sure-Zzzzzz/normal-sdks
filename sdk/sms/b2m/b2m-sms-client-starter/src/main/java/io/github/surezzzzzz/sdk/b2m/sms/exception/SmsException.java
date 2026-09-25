package io.github.surezzzzzz.sdk.b2m.sms.exception;

import lombok.Getter;

/**
 * B2M 短信客户端基础异常：SDK 故障（配置/加密/通信/解析）用它抛出；
 * 平台业务结果（发送被拒等）不抛异常，进 {@code SmsSendResult} 由调用方判断。
 *
 * @author surezzzzzz
 */
@Getter
public class SmsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码（见 ErrorCode 常量）。
     */
    private final String errorCode;

    public SmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SmsException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
