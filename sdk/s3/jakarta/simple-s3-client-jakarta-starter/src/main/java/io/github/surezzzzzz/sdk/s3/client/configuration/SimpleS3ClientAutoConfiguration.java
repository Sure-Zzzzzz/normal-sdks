package io.github.surezzzzzz.sdk.s3.client.configuration;

import io.github.surezzzzzz.sdk.s3.client.SimpleS3ClientPackage;
import io.github.surezzzzzz.sdk.s3.client.annotation.SimpleS3ClientComponent;
import io.github.surezzzzzz.sdk.s3.client.constant.SimpleS3ClientConstant;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * S3 Client 自动配置。业务组件（门面）经受限扫描链注册；
 * 无第三方类实例化与复杂初始化，不使用 @Bean 方法。
 *
 * @author surezzzzzz
 */
@AutoConfiguration
@EnableConfigurationProperties(SimpleS3ClientProperties.class)
@ConditionalOnClass(S3RouteTemplate.class)
@ComponentScan(basePackageClasses = SimpleS3ClientPackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleS3ClientComponent.class))
@ConditionalOnProperty(prefix = SimpleS3ClientConstant.CONFIG_PREFIX,
        name = SimpleS3ClientConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleS3ClientConstant.BOOLEAN_TRUE)
public class SimpleS3ClientAutoConfiguration {
}
