package io.github.surezzzzzz.sdk.audit.limiter.management.configuration;

import io.github.surezzzzzz.sdk.audit.limiter.management.SmartRedisLimiterManagementAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.limiter.management.annotation.SmartRedisLimiterManagementAuditListenerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.annotation.PostConstruct;

/**
 * 限流管理审计监听器自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(SmartRedisLimiterManagementAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SmartRedisLimiterManagementAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterManagementAuditListenerComponent.class),
        useDefaultFilters = false
)
public class SmartRedisLimiterManagementAuditListenerAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== SmartRedisLimiter ManagementAuditListener 自动配置加载成功 =====");
    }
}
