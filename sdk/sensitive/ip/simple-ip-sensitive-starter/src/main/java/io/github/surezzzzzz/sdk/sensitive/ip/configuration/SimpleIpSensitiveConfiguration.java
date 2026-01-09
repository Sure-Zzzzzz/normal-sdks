package io.github.surezzzzzz.sdk.sensitive.ip.configuration;

import io.github.surezzzzzz.sdk.sensitive.ip.SimpleIpSensitivePackage;
import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.ip.constant.SimpleIpSensitiveConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple IP Sensitive Auto Configuration
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleIpSensitiveProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIpSensitivePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIpSensitiveComponent.class)
)
@ConditionalOnProperty(prefix = SimpleIpSensitiveConstant.CONFIG_PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
public class SimpleIpSensitiveConfiguration {
}
