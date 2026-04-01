package io.github.surezzzzzz.sdk.audit.aksk.configuration;

import io.github.surezzzzzz.sdk.audit.aksk.SimpleAkskAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.aksk.annotation.SimpleAkskAuditListenerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AKSK 审计监听器自动配置
 *
 * <p>当业务实现了 AkskAuditHandler 接口时，自动注册事件监听器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(AkskAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskAuditListenerComponent.class)
)
public class AkskAuditListenerAutoConfiguration {
}
