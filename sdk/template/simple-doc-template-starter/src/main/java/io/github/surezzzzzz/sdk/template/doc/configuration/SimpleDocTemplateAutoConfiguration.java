package io.github.surezzzzzz.sdk.template.doc.configuration;

import io.github.surezzzzzz.sdk.template.doc.SimpleDocTemplatePackage;
import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Doc Template Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleDocTemplateProperties.class)
@ComponentScan(
        basePackageClasses = SimpleDocTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleDocTemplateComponent.class)
)
@ConditionalOnProperty(
        prefix = SimpleDocTemplateConstant.CONFIG_PREFIX,
        name = "enable",
        havingValue = "true"
)
public class SimpleDocTemplateAutoConfiguration {
}