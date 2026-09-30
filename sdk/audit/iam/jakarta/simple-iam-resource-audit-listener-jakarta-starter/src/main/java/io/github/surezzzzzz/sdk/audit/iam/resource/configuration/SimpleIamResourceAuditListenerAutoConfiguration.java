package io.github.surezzzzzz.sdk.audit.iam.resource.configuration;

import io.github.surezzzzzz.sdk.audit.iam.resource.SimpleIamResourceAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.iam.resource.annotation.SimpleIamResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.resource.constant.SimpleIamResourceAuditListenerConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

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
}
