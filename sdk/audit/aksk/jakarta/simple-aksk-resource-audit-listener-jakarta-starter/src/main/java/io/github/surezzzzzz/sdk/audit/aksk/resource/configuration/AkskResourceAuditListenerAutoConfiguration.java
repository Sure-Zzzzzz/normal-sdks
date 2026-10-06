package io.github.surezzzzzz.sdk.audit.aksk.resource.configuration;

import io.github.surezzzzzz.sdk.audit.aksk.resource.SimpleAkskResourceAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.aksk.resource.annotation.SimpleAkskResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.resource.constant.AkskResourceAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.listener.AkskAuditEventListener;
import io.github.surezzzzzz.sdk.audit.aksk.resource.provider.AkskAuditTraceIdProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;

/**
 * AKSK 资源审计的 Jakarta 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(AkskResourceAuditListenerProperties.class)
@ConditionalOnProperty(prefix = AkskResourceAuditListenerConstant.CONFIG_PREFIX,
        name = "enable", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = SimpleAkskResourceAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskResourceAuditListenerComponent.class),
        useDefaultFilters = false)
public class AkskResourceAuditListenerAutoConfiguration {
    /**
     * 在宿主 Bean 定义可见后判断 Handler 条件，不在组件扫描阶段提前排除监听器。
     *
     * @param auditHandlers   全部业务和日志处理器
     * @param traceIdProvider 可选追踪标识提供者
     * @return 官方监听器；宿主已提供同类型监听器时让位
     */
    @Bean
    @ConditionalOnBean(AkskAuditHandler.class)
    @ConditionalOnMissingBean(AkskAuditEventListener.class)
    public AkskAuditEventListener akskAuditEventListener(List<AkskAuditHandler> auditHandlers,
                                                         ObjectProvider<AkskAuditTraceIdProvider> traceIdProvider) {
        return new AkskAuditEventListener(auditHandlers, traceIdProvider.getIfAvailable());
    }
}
