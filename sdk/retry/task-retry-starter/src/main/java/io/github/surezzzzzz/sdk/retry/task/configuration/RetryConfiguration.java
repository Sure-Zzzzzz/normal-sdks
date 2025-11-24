package io.github.surezzzzzz.sdk.retry.task.configuration;

import io.github.surezzzzzz.sdk.retry.task.RetryPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ComponentScan(basePackageClasses = RetryPackage.class, includeFilters = @ComponentScan.Filter(RetryComponent.class))
public class RetryConfiguration {
}
