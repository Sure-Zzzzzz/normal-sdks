package io.github.surezzzzzz.sdk.audit.iam.resource.configuration;

import io.github.surezzzzzz.sdk.audit.iam.resource.SimpleIamResourceAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.iam.resource.annotation.SimpleIamResourceAuditListenerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Simple IAM Resource Audit Listener 自动配置
 *
 * <p>仅扫描本模块自定义注解标记的组件；监听器仅在存在
 * {@code IamResourceAuditHandler} 时注册。启用异步执行器供监听器分发使用。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(SimpleIamResourceAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamResourceAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamResourceAuditListenerComponent.class),
        useDefaultFilters = false
)
public class SimpleIamResourceAuditListenerAutoConfiguration {
}
