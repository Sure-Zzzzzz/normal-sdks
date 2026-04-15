package io.github.surezzzzzz.sdk.audit.aksk.server.configuration;

import io.github.surezzzzzz.sdk.audit.aksk.server.SimpleAkskServerAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.aksk.server.annotation.SimpleAkskServerAuditListenerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AKSK Server 审计监听器自动配置
 *
 * <p>当业务实现了 ServerTokenAuditHandler 接口时，自动注册事件监听器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(SimpleAkskServerAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskServerAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskServerAuditListenerComponent.class)
)
public class SimpleAkskServerAuditListenerAutoConfiguration {
}
