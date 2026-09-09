package io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.SimpleIamResourceServerPackage;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.annotation.SimpleIamResourceServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.SimpleIamResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.IamResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple IAM Resource Server 自动配置
 *
 * <p>仅扫描本模块自定义注解标记的组件；未配置验证端点时不装配任何 IAM Provider 组件。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleIamResourceServerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamResourceServerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamResourceServerComponent.class),
        useDefaultFilters = false
)
@ConditionalOnProperty(prefix = SimpleIamResourceServerConstant.CONFIG_PREFIX,
        name = "verification-endpoint")
public class SimpleIamResourceServerAutoConfiguration {

    /**
     * 创建IAM受控令牌验证客户端。
     *
     * @param properties IAM资源验证配置
     * @return IAM受控令牌验证客户端
     */
    @Bean
    @ConditionalOnMissingBean(HttpIamResourceTokenVerificationClient.class)
    public HttpIamResourceTokenVerificationClient iamResourceTokenVerificationClient(
            SimpleIamResourceServerProperties properties) {
        log.info("创建IAM受控令牌验证客户端，端点={}", properties.getVerificationEndpoint());
        return new HttpIamResourceTokenVerificationClient(properties);
    }

    /**
     * 注册IAM资源认证适配器。
     *
     * @param verificationClient IAM受控令牌验证客户端
     * @return IAM资源认证适配器
     */
    @Bean
    @ConditionalOnMissingBean(name = "iamResourceAuthenticationAdapter")
    public ResourceAuthenticationAdapter iamResourceAuthenticationAdapter(
            HttpIamResourceTokenVerificationClient verificationClient) {
        log.info("注册IAM资源认证适配器");
        return new IamResourceAuthenticationAdapter(verificationClient);
    }
}
