package io.github.surezzzzzz.sdk.audit.iam.resource.configuration;

import io.github.surezzzzzz.sdk.audit.iam.resource.SimpleIamResourceAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.iam.resource.annotation.SimpleIamResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.resource.constant.SimpleIamResourceAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.listener.IamResourceAuditEventListener;
import io.github.surezzzzzz.sdk.audit.iam.resource.provider.IamResourceAuditTraceIdProvider;
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
 * Simple IAM Resource Audit Listener 自动配置。
 *
 * <p>仅扫描本模块自定义注解标记的组件；监听器仅在存在
 * {@code IamResourceAuditHandler} 时注册。启用异步执行器供监听器分发使用。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@EnableAsync
// 总开关：enable=false 时整个审计监听器（含默认日志 Handler 与异步执行器）不装配；
// 停用审计优先用本开关，不必移除依赖
@ConditionalOnProperty(prefix = SimpleIamResourceAuditListenerConstant.CONFIG_PREFIX,
        name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpleIamResourceAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamResourceAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamResourceAuditListenerComponent.class),
        useDefaultFilters = false
)
public class SimpleIamResourceAuditListenerAutoConfiguration {

    /**
     * 在宿主和扫描组件的 Bean 定义注册后装配监听器，避免扫描阶段提前判断 Handler 条件。
     *
     * @param auditHandlers   全部审计处理器
     * @param traceIdProvider 可选追踪标识提供者
     * @return 审计事件监听器；宿主已有监听器时让位
     */
    @Bean
    @ConditionalOnBean(IamResourceAuditHandler.class)
    @ConditionalOnMissingBean(IamResourceAuditEventListener.class)
    public IamResourceAuditEventListener iamResourceAuditEventListener(
            List<IamResourceAuditHandler> auditHandlers,
            ObjectProvider<IamResourceAuditTraceIdProvider> traceIdProvider) {
        return new IamResourceAuditEventListener(auditHandlers, traceIdProvider.getIfAvailable());
    }
}
