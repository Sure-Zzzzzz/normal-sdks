package io.github.surezzzzzz.sdk.b2m.sms.configuration;

import io.github.surezzzzzz.sdk.b2m.sms.SmsPackage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/11 16:47
 */
@Configuration
@ComponentScan(basePackageClasses = SmsPackage.class, includeFilters = @ComponentScan.Filter(SmsComponent.class))
public class SmsConfiguration {

    @Bean
    @Qualifier("smsRestTemplate")
    public RestTemplate restTemplate() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }
}
