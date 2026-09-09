package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.SimpleIamLdapAdapterPackage;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.annotation.SimpleIamLdapAdapterComponent;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple IAM LDAP Adapter Auto Configuration
 *
 * <p>引依赖即装配，无 enable 开关；宿主不引入本 starter 即不装配，
 * 引入后 url 必填校验启动期快速失败。仅扫描本模块自定义注解标记的组件；
 * 认证器的 ContextSource / BindAuthenticator 组装在组件构造内自治完成，无需 @Bean 装配。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnClass(ExternalCredentialAuthenticator.class)
@EnableConfigurationProperties(SimpleIamLdapAdapterProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamLdapAdapterPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamLdapAdapterComponent.class),
        useDefaultFilters = false
)
public class SimpleIamLdapAdapterAutoConfiguration {
}
