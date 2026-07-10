package io.github.surezzzzzz.sdk.mail.configuration;

import io.github.surezzzzzz.sdk.mail.MailPackage;
import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mail 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
@ComponentScan(
        basePackageClasses = MailPackage.class,
        includeFilters = @ComponentScan.Filter(MailComponent.class)
)
@ConditionalOnProperty(prefix = MailConstant.CONFIG_PREFIX, name = MailConstant.PROPERTY_ENABLE, havingValue = MailConstant.PROPERTY_TRUE)
public class MailConfiguration {
}
