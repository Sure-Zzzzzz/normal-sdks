package io.github.surezzzzzz.sdk.audit.aksk.server.configuration;

import io.github.surezzzzzz.sdk.audit.aksk.server.SimpleAkskServerAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.aksk.server.annotation.SimpleAkskServerAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.listener.ServerTokenAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AKSK Server 审计监听器自动配置
 *
 * <p>仅扫描本模块自定义注解标记的组件；监听器本身仅在业务提供
 * {@code ServerTokenAuditHandler} 时注册。不启用异步执行器，以保证审计处理的提交后语义由事务事件机制统一控制。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleAkskServerAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskServerAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskServerAuditListenerComponent.class),
        useDefaultFilters = false
)
public class SimpleAkskServerAuditListenerAutoConfiguration {

    /**
     * 注册提交后 Token 审计监听器。
     *
     * @param auditHandlers 审计处理器
     * @return Token 审计监听器
     */
    @Bean
    @ConditionalOnBean(ServerTokenAuditHandler.class)
    public ServerTokenAuditEventListener serverTokenAuditEventListener(
            List<ServerTokenAuditHandler> auditHandlers) {
        return new ServerTokenAuditEventListener(auditHandlers);
    }
}
