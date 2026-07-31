package io.github.surezzzzzz.sdk.log.truncate.configuration;

import io.github.surezzzzzz.sdk.log.truncate.LogTruncatePackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 日志截断自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@ComponentScan(
        basePackageClasses = LogTruncatePackage.class,
        includeFilters = @ComponentScan.Filter(LogTruncateComponent.class),
        useDefaultFilters = false
)
public class LogTruncateConfiguration {
}
