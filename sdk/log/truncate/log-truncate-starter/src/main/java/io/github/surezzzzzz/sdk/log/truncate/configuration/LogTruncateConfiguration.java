package io.github.surezzzzzz.sdk.log.truncate.configuration;

import io.github.surezzzzzz.sdk.log.truncate.LogTruncatePackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ComponentScan(basePackageClasses = LogTruncatePackage.class, includeFilters = @ComponentScan.Filter(LogTruncateComponent.class))
public class LogTruncateConfiguration {

}
