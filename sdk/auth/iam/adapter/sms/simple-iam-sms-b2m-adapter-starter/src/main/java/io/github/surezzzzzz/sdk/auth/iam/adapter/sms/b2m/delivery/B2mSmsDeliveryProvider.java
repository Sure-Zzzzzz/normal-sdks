package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.delivery;

import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.annotation.B2mSmsDeliveryComponent;
import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.configuration.B2mSmsDeliveryProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.constant.B2mSmsDeliveryConstant;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.SmsDeliveryProvider;
import io.github.surezzzzzz.sdk.b2m.sms.client.SmsClient;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;
import io.github.surezzzzzz.sdk.b2m.sms.model.SmsSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * B2M 投递实现：deliver 走 SmsClient 单条通道直发（正文=签名+话术，{code} 替换；customSmsId=purpose,跨层日志关联）；平台业务拒绝不抛异常、由本层查 success 失败即抛（不吞投递失败）；
 * supportedRegions 宣称 +86（能力自述,出入口统一消费）。
 *
 * @author surezzzzzz
 */
@Slf4j
@B2mSmsDeliveryComponent
@RequiredArgsConstructor
public class B2mSmsDeliveryProvider implements SmsDeliveryProvider {

    private final SmsClient smsClient;
    private final B2mSmsDeliveryProperties properties;

    @Override
    public void deliver(String phone, String code, String purpose, int expiresInSeconds) {
        String text = signature() + textOf(purpose)
                .replace(B2mSmsDeliveryConstant.CODE_PLACEHOLDER, code)
                .replace(B2mSmsDeliveryConstant.MINUTES_PLACEHOLDER,
                        String.valueOf(Math.max(1, expiresInSeconds / 60)));
        log.debug("B2M投递(单条直发): purpose={}", purpose);
        SmsSendResult result = smsClient.sendSingleSms(phone, text, purpose);
        if (!result.isSuccess()) {
            throw new SmsException(result.getResultCode(),
                    "B2M平台拒绝投递: " + result.getMessage() + ", customSmsId=" + purpose);
        }
    }

    @Override
    public List<String> supportedRegions() {
        return B2mSmsDeliveryConstant.SUPPORTED_REGIONS;
    }

    private String textOf(String purpose) {
        if (SimpleIamCoreConstant.SMS_PURPOSE_LOGIN.equals(purpose)) {
            return properties.getLoginText();
        }
        if (SimpleIamCoreConstant.SMS_PURPOSE_BIND.equals(purpose)) {
            return properties.getBindText();
        }
        if (SimpleIamCoreConstant.SMS_PURPOSE_FORGOT_PASSWORD.equals(purpose)) {
            return properties.getForgotPasswordText();
        }
        // 未知用途静默落登录话术会给用户发出语义错误的短信，宁可失败关闭
        throw new io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.exception.B2mSmsDeliveryException(
                "未知短信用途（不在 SMS_PURPOSE_* 契约内）: " + purpose);
    }

    private String signature() {
        return properties.getSignature() == null ? "" : properties.getSignature();
    }
}
