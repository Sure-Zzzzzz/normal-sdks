package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.SimpleIamPortalGatewayAdaptorPackage;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 2.7.9 自动配置；引用 starter 即装配 adaptor 控制面。
 */
@Configuration
@EnableConfigurationProperties(SimpleIamPortalGatewayAdaptorProperties.class)
@EnableScheduling
@ComponentScan(
        basePackageClasses = SimpleIamPortalGatewayAdaptorPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SimpleIamPortalGatewayAdaptorComponent.class))
public class SimpleIamPortalGatewayAdaptorAutoConfiguration {
}
