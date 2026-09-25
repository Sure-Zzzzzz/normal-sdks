package io.github.surezzzzzz.sdk.b2m.sms.exception;

/**
 * B2M 短信配置异常（启动期：必填缺失 / 密钥长度非法 / Provider 缺失），显式失败不留中间态。
 *
 * @author surezzzzzz
 */
public class SmsConfigurationException extends SmsException {

    private static final long serialVersionUID = 1L;

    public SmsConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SmsConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
