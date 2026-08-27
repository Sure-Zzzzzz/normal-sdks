package io.github.surezzzzzz.sdk.s3.route.configuration;

import io.github.surezzzzzz.sdk.s3.route.SimpleS3RoutePackage;
import io.github.surezzzzzz.sdk.s3.route.annotation.SimpleS3RouteComponent;
import io.github.surezzzzzz.sdk.s3.route.client.DefaultS3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.client.S3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.constant.SimpleS3RouteConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * S3 Route 自动配置。业务组件（校验器、注册表、解析器、门面）经受限扫描链注册；
 * ClientFactory 以 @Bean 注册以提供业务整体替换扩展点。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleS3RouteProperties.class)
@ComponentScan(basePackageClasses = SimpleS3RoutePackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleS3RouteComponent.class))
@ConditionalOnClass(name = "com.amazonaws.services.s3.AmazonS3")
@ConditionalOnProperty(prefix = SimpleS3RouteConstant.CONFIG_PREFIX,
        name = SimpleS3RouteConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleS3RouteConstant.BOOLEAN_TRUE)
public class SimpleS3RouteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(S3RouteClientFactory.class)
    public S3RouteClientFactory s3RouteClientFactory() {
        return new DefaultS3RouteClientFactory();
    }
}
