package io.github.surezzzzzz.sdk.audit.aksk.resource.configuration;

import io.github.surezzzzzz.sdk.audit.aksk.resource.SimpleAkskResourceAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.aksk.resource.annotation.SimpleAkskResourceAuditListenerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AKSK Resource 审计监听器自动配置
 *
 * <p>当业务实现了 AkskAuditHandler 接口时，自动注册事件监听器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(AkskResourceAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskResourceAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskResourceAuditListenerComponent.class)
)
public class AkskResourceAuditListenerAutoConfiguration {
}
