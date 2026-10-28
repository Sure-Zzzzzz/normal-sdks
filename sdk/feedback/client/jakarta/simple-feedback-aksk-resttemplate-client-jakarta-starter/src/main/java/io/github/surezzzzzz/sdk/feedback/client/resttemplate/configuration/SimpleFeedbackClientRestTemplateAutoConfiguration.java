package io.github.surezzzzzz.sdk.feedback.client.resttemplate.configuration;

import io.github.surezzzzzz.sdk.feedback.client.FeedbackClient;
import io.github.surezzzzzz.sdk.feedback.client.resttemplate.FeedbackRestTemplateClient;
import io.github.surezzzzzz.sdk.feedback.client.resttemplate.SimpleFeedbackClientRestTemplatePackage;
import io.github.surezzzzzz.sdk.feedback.client.resttemplate.annotation.SimpleFeedbackClientRestTemplateComponent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 传输自动配置。认证、连接池和 token 均由 AKSK 底座提供，
 * 本模块只负责 Feedback wire 映射。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RestTemplate.class, FeedbackClient.class})
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.feedback.client",
        name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpleFeedbackClientRestTemplateProperties.class)
@ComponentScan(basePackageClasses = SimpleFeedbackClientRestTemplatePackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleFeedbackClientRestTemplateComponent.class))
public class SimpleFeedbackClientRestTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeedbackClient.class)
    public FeedbackClient feedbackClient(
            @Qualifier("akskClientRestTemplate") RestTemplate restTemplate,
            SimpleFeedbackClientRestTemplateProperties properties) {
        return new FeedbackRestTemplateClient(restTemplate, properties.getBaseUrl());
    }
}
