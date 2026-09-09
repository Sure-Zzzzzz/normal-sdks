package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.server.service.ExternalProviderRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部身份源登录方式装配配置
 *
 * <p>以 ObjectProvider 收集独立适配器模块注册的 SPI 实现；未装配任何适配器时注册表为空，
 * IAM 保持既有本地账号密码登录行为。
 *
 * @author surezzzzzz
 */
@Configuration
public class ExternalIdentityProviderConfiguration {

    /**
     * 注册外部登录方式注册表。
     *
     * <p>@Bean 方式注册：注册表需要聚合收集容器内全部 SPI 实现并做编码冲突快速失败，
     * 无法以自定义注解组件方式表达。
     *
     * @param credentialAuthenticators 已装配的凭证校验型认证器
     * @param browserLoginProviders    已装配的跳转型登录提供方
     * @return 外部登录方式注册表
     */
    @Bean
    public ExternalProviderRegistry externalProviderRegistry(
            ObjectProvider<ExternalCredentialAuthenticator> credentialAuthenticators,
            ObjectProvider<ExternalBrowserLoginProvider> browserLoginProviders) {
        return ExternalProviderRegistry.create(
                credentialAuthenticators.orderedStream()
                        .collect(java.util.stream.Collectors.toList()),
                browserLoginProviders.orderedStream()
                        .collect(java.util.stream.Collectors.toList()));
    }
}
