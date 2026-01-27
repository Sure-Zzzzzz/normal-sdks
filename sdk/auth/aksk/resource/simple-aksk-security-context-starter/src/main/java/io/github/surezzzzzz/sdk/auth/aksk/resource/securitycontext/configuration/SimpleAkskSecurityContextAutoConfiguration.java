package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.SimpleAkskSecurityContextPackage;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.SimpleAkskSecurityContextComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant.SimpleAkskSecurityContextConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.filter.AkskSecurityContextFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK Security Context 自动配置
 *
 * <p>自动注册 AkskSecurityContextFilter，从 HTTP Header 提取用户信息。
 *
 * <p>配置项：
 * <ul>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.security-context.enable: 是否启用（默认：true）</li>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.security-context.header-prefix: Header 前缀（默认：x-sure-auth-aksk-）</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleAkskSecurityContextProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskSecurityContextPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskSecurityContextComponent.class)
)
@ConditionalOnProperty(
        prefix = SimpleAkskSecurityContextConstant.CONFIG_PREFIX,
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class SimpleAkskSecurityContextAutoConfiguration {

    private final SimpleAkskSecurityContextProperties properties;

    public SimpleAkskSecurityContextAutoConfiguration(SimpleAkskSecurityContextProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("Simple AKSK Security Context Starter initialized");
        log.info("Header prefix: {}", properties.getHeaderPrefix());
    }

    /**
     * 注册 AkskSecurityContextFilter
     *
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<AkskSecurityContextFilter> akskSecurityContextFilter() {
        FilterRegistrationBean<AkskSecurityContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AkskSecurityContextFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("akskSecurityContextFilter");
        log.info("AkskSecurityContextFilter registered");
        return registration;
    }
}
