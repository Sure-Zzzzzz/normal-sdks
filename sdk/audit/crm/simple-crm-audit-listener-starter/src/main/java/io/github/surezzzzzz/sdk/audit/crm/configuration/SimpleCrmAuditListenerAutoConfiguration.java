package io.github.surezzzzzz.sdk.audit.crm.configuration;

import io.github.surezzzzzz.sdk.audit.crm.SimpleCrmAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.crm.annotation.SimpleCrmAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.crm.handler.CrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.listener.CrmAuditEventDispatcher;
import io.github.surezzzzzz.sdk.audit.crm.listener.CrmAuditEventListener;
import io.github.surezzzzzz.sdk.audit.crm.provider.CrmAuditTraceIdProvider;
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
 * CRM 审计监听器自动配置。
 *
 * <p>先扫描宿主提供的处理器和本模块默认处理器，再仅在至少存在一个处理器时注册事件监听器。
 * 这样默认日志处理器的配置开关可以真正参与 Bean 条件判断。</p>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(SimpleCrmAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleCrmAuditListenerPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleCrmAuditListenerComponent.class)
)
public class SimpleCrmAuditListenerAutoConfiguration {

    /**
     * 注册异步审计分发器。
     *
     * @param auditHandlers 已装配的审计处理器
     * @return 审计分发器
     */
    @Bean
    @ConditionalOnBean(CrmAuditHandler.class)
    @ConditionalOnMissingBean(CrmAuditEventDispatcher.class)
    public CrmAuditEventDispatcher crmAuditEventDispatcher(List<CrmAuditHandler> auditHandlers) {
        return new CrmAuditEventDispatcher(auditHandlers);
    }

    /**
     * 注册 CRM 审计事件监听器。
     *
     * @param dispatcher      异步审计分发器
     * @param traceIdProvider 可选链路追踪标识提供者
     * @return CRM 审计事件监听器
     */
    @Bean
    @ConditionalOnBean(CrmAuditHandler.class)
    @ConditionalOnMissingBean(CrmAuditEventListener.class)
    public CrmAuditEventListener crmAuditEventListener(CrmAuditEventDispatcher dispatcher,
                                                        ObjectProvider<CrmAuditTraceIdProvider> traceIdProvider) {
        return new CrmAuditEventListener(dispatcher, traceIdProvider.getIfAvailable());
    }
}
