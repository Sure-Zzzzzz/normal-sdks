package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.exception;

/**
 * B2M 投递适配器业务异常（投递入参违反 SPI 契约等运行期失败），
 * 不复用配置期异常或平台通道码，避免误导排障方向。
 *
 * @author surezzzzzz
 */
public class B2mSmsDeliveryException extends RuntimeException {

    public B2mSmsDeliveryException(String message) {
        super(message);
    }
}
