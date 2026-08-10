package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.spi.ApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.DefaultApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.server.SimpleResourceServerPackage;
import io.github.surezzzzzz.sdk.auth.resource.server.annotation.SimpleResourceServerComponent;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.resource.server.support.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 通用资源服务自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(ResourceServerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleResourceServerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleResourceServerComponent.class),
        useDefaultFilters = false
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = SimpleResourceServerStarterConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@org.springframework.context.annotation.Conditional(ResourceServerProtectedPathCondition.class)
@Import({ResourceServerLegacySecurityConfiguration.class, ResourceServerModernSecurityConfiguration.class})
public class ResourceServerAutoConfiguration {

    /**
     * 创建默认Bearer凭据解析器。
     *
     * @return Bearer凭据解析器
     */
    @Bean
    @ConditionalOnMissingBean(ResourceServerEngine.class)
    public BearerCredentialResolver bearerCredentialResolver() {
        return new BearerCredentialResolver();
    }

    /**
     * 创建认证适配器注册表。
     *
     * @param adapters Provider认证适配器
     * @return 认证适配器注册表
     */
    @Bean
    @ConditionalOnMissingBean(ResourceServerEngine.class)
    public ResourceAuthenticationAdapterRegistry resourceAuthenticationAdapterRegistry(
            List<ResourceAuthenticationAdapter> adapters) {
        if (adapters.isEmpty()) {
            throw new ResourceServerConfigurationException(
                    SimpleResourceServerStarterConstant.ERROR_MISSING_AUTHENTICATION_ADAPTER);
        }
        return new DefaultResourceAuthenticationAdapterRegistry(adapters);
    }

    /**
     * 创建默认资源认证编排引擎。
     *
     * @param credentialResolver Bearer凭据解析器
     * @param adapterRegistry    认证适配器注册表
     * @return 资源认证编排引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public ResourceServerEngine resourceServerEngine(BearerCredentialResolver credentialResolver,
                                                     ResourceAuthenticationAdapterRegistry adapterRegistry) {
        return new DefaultResourceServerEngine(credentialResolver, adapterRegistry);
    }

    /**
     * 创建默认精确API权限判定器。
     *
     * @return 精确API权限判定器
     */
    @Bean
    @ConditionalOnMissingBean
    public ApplicationAuthorizationEvaluator applicationAuthorizationEvaluator() {
        return new DefaultApplicationAuthorizationEvaluator();
    }

    /**
     * 创建配置化精确API权限规则解析器。
     *
     * @param properties  资源服务配置
     * @param environment Spring环境
     * @return 配置化精确API权限规则解析器
     */
    @Bean
    public ConfiguredApiPermissionResolver configuredApiPermissionResolver(ResourceServerProperties properties,
                                                                           Environment environment) {
        return new ConfiguredApiPermissionResolver(properties.getSecurity(), environment.getProperty(
                SimpleResourceServerStarterConstant.PROPERTY_SERVER_SERVLET_CONTEXT_PATH));
    }

    /**
     * 创建MVC精确API权限配置。
     *
     * @param evaluator 精确API权限判定器
     * @param resolver  配置化精确API权限规则解析器
     * @return MVC精确API权限配置
     */
    @Bean
    public WebMvcConfigurer resourceServerWebMvcConfigurer(ApplicationAuthorizationEvaluator evaluator,
                                                           ConfiguredApiPermissionResolver resolver) {
        return new ResourceServerWebMvcConfiguration(evaluator, resolver);
    }

}
