package io.github.surezzzzzz.sdk.b2m.sms.configuration;

import io.github.surezzzzzz.sdk.b2m.sms.SmsPackage;
import io.github.surezzzzzz.sdk.b2m.sms.annotation.SmsComponent;
import io.github.surezzzzzz.sdk.b2m.sms.constant.SmsConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * B2M 短信自动配置：enable=true 装配 SmsClient（构造期校验必填项与密钥长度，缺失显式启动失败）；
 * smsRestTemplate 自建专用实例，不注入应用容器 RestTemplate。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SmsProperties.class)
@ComponentScan(
        basePackageClasses = SmsPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SmsComponent.class)
)
@ConditionalOnProperty(prefix = SmsConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmsConfiguration {

    /**
     * 自建 RestTemplate（专用连接行为，不与应用容器共享；带连接/读取超时，防平台无响应时线程挂死）。
     *
     * @param smsProperties 配置
     * @return RestTemplate 实例
     */
    @Bean(SmsConstant.BEAN_SMS_REST_TEMPLATE)
    public RestTemplate smsRestTemplate(SmsProperties smsProperties) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(smsProperties.getConnectTimeoutMs());
        factory.setReadTimeout(smsProperties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
