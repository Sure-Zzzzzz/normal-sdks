package io.github.surezzzzzz.sdk.audit.iam.server.configuration;

import io.github.surezzzzzz.sdk.audit.iam.server.SimpleIamServerAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.iam.server.annotation.SimpleIamServerAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.listener.ServerIamAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * IAM 审计监听器自动配置
 *
 * <p>仅扫描本模块自定义注解标记的组件；监听器本身仅在存在
 * {@code ServerIamAuditHandler} 时注册。不启用异步执行器，以保证审计处理的提交后语义由事务事件机制统一控制。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleIamServerAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamServerAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamServerAuditListenerComponent.class),
        useDefaultFilters = false
)
public class SimpleIamServerAuditListenerAutoConfiguration {

    /**
     * 注册提交后 IAM 审计监听器。
     *
     * @param auditHandlers 审计处理器
     * @return IAM 审计监听器
     */
    @Bean
    @ConditionalOnBean(ServerIamAuditHandler.class)
    public ServerIamAuditEventListener iamAuditEventListener(List<ServerIamAuditHandler> auditHandlers) {
        return new ServerIamAuditEventListener(auditHandlers);
    }
}
