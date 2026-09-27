package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.constant.B2mSmsDeliveryConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * B2M 投递配置：enable/签名/三套话术（默认内置常量可覆盖）。单条通道直发，不走平台模板。
 * adaptor 自身零密钥——B2M 凭据（app-id/secret-key）配置在 b2m-sms-client-starter 层。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = B2mSmsDeliveryConstant.CONFIG_PREFIX)
public class B2mSmsDeliveryProperties {

    /**
     * 是否启用（默认 false，等价未装配）
     */
    private boolean enable = false;

    /**
     * 短信签名（adaptor 固定拼接在正文头；单条通道由本配置提供签名）
     */
    private String signature;

    /**
     * 登录话术（默认内置，含【签名形态启动失败）
     */
    private String loginText = B2mSmsDeliveryConstant.DEFAULT_TEXT_LOGIN;

    /**
     * 绑定话术
     */
    private String bindText = B2mSmsDeliveryConstant.DEFAULT_TEXT_BIND;

    /**
     * 忘记密码话术
     */
    private String forgotPasswordText = B2mSmsDeliveryConstant.DEFAULT_TEXT_FORGOT_PASSWORD;
}
