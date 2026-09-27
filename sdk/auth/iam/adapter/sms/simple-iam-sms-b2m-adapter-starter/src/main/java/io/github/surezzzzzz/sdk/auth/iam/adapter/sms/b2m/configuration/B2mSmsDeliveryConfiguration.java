package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.B2mSmsDeliveryPackage;
import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.annotation.B2mSmsDeliveryComponent;
import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.constant.B2mSmsDeliveryConstant;
import io.github.surezzzzzz.sdk.b2m.sms.constant.ErrorCode;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * B2M 投递自动配置：enable=true 装配（等价能力存在）；话术含【】签名形态启动失败（签名由独立配置拼接）；
 * 前置依赖 b2m-sms-client-starter 的 SmsClient bean（其 enable=true 才存在,联动失效即本装配失败,
 * 符合"不产生装了 adaptor 但投递不可用的静默态"）。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(B2mSmsDeliveryProperties.class)
@ComponentScan(
        basePackageClasses = B2mSmsDeliveryPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(B2mSmsDeliveryComponent.class)
)
@ConditionalOnProperty(prefix = B2mSmsDeliveryConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class B2mSmsDeliveryConfiguration {

    B2mSmsDeliveryConfiguration(B2mSmsDeliveryProperties properties) {
        validate(properties);
    }

    private void validate(B2mSmsDeliveryProperties properties) {
        if (properties.getLoginText().contains(B2mSmsDeliveryConstant.SIGNATURE_BRACKET_OPEN)
                || properties.getBindText().contains(B2mSmsDeliveryConstant.SIGNATURE_BRACKET_OPEN)
                || properties.getForgotPasswordText().contains(B2mSmsDeliveryConstant.SIGNATURE_BRACKET_OPEN)) {
            throw new SmsConfigurationException(ErrorCode.CONFIG_CONTENT_INVALID,
                    "B2M投递话术非法：模板只是正文,含【】签名形态会被拒绝（签名由独立配置拼接）");
        }
        log.info("B2M投递装配完成: regions={}", B2mSmsDeliveryConstant.SUPPORTED_REGIONS);
    }
}
