package io.github.surezzzzzz.sdk.audit.license.configuration;

import io.github.surezzzzzz.sdk.audit.license.SimpleLicenseAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.license.annotation.SimpleLicenseAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.listener.LicenseAuditEventListener;
import io.github.surezzzzzz.sdk.audit.license.provider.LicenseAuditTraceIdProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;

/**
 * License 审计监听器自动配置
 *
 * <p>当宿主实现了 {@code LicenseAuditHandler} 时，自动注册事件监听器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(LicenseAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleLicenseAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleLicenseAuditListenerComponent.class),
        useDefaultFilters = false
)
public class LicenseAuditListenerAutoConfiguration {

    /**
     * 在宿主及扫描件的 Bean 定义注册后判断处理器，避免扫描阶段过早跳过监听器。
     *
     * @param auditHandlers   宿主及默认日志处理器
     * @param traceIdProvider 可选链路标识提供者
     * @return 官方监听器；宿主已提供同类型监听器时让位
     */
    @Bean
    @ConditionalOnBean(LicenseAuditHandler.class)
    @ConditionalOnMissingBean(LicenseAuditEventListener.class)
    public LicenseAuditEventListener licenseAuditEventListener(
            List<LicenseAuditHandler> auditHandlers,
            ObjectProvider<LicenseAuditTraceIdProvider> traceIdProvider) {
        return new LicenseAuditEventListener(auditHandlers, traceIdProvider.getIfAvailable());
    }
}
