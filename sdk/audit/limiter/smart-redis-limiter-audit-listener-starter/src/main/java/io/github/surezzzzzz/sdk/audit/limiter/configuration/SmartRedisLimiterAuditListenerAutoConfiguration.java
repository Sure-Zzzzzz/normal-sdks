package io.github.surezzzzzz.sdk.audit.limiter.configuration;

import io.github.surezzzzzz.sdk.audit.limiter.SmartRedisLimiterAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.annotation.PostConstruct;

/**
 * SmartRedisLimiter 限流审计监听器自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(SmartRedisLimiterAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SmartRedisLimiterAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterAuditListenerComponent.class),
        useDefaultFilters = false
)
public class SmartRedisLimiterAuditListenerAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== SmartRedisLimiter AuditListener 自动配置加载成功 =====");
    }
}
