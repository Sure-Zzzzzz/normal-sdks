package io.github.surezzzzzz.sdk.retry.task.configuration;

import io.github.surezzzzzz.sdk.retry.task.TaskRetryPackage;
import io.github.surezzzzzz.sdk.retry.task.annotation.TaskRetryComponent;
import io.github.surezzzzzz.sdk.retry.task.constant.TaskRetryConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Task Retry 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(TaskRetryProperties.class)
@ComponentScan(
        basePackageClasses = TaskRetryPackage.class,
        includeFilters = @ComponentScan.Filter(TaskRetryComponent.class)
)
@ConditionalOnProperty(prefix = TaskRetryConstant.CONFIG_PREFIX, name = TaskRetryConstant.PROPERTY_ENABLE, havingValue = TaskRetryConstant.PROPERTY_TRUE, matchIfMissing = true)
public class TaskRetryAutoConfiguration {
}
