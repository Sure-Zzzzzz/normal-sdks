package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Spring Boot 2.2和2.3资源服务安全配置。
 *
 * @author surezzzzzz
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@org.springframework.context.annotation.Conditional(ResourceServerBootVersionCondition.Legacy.class)
public class ResourceServerLegacySecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final ResourceServerProperties properties;
    private final ResourceServerEngine engine;
    private final Environment environment;

    /**
     * 创建旧版资源服务安全配置。
     *
     * @param properties  资源服务配置
     * @param engine      资源认证编排引擎
     * @param environment Spring环境
     */
    public ResourceServerLegacySecurityConfiguration(ResourceServerProperties properties, ResourceServerEngine engine,
                                                     Environment environment) {
        this.properties = properties;
        this.engine = engine;
        this.environment = environment;
    }

    /**
     * 配置旧版Spring Security资源安全链。
     *
     * @param http Spring Security配置器
     * @throws Exception 配置异常
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        ResourceServerSecurityConfigurer.configure(http, properties, engine, environment);
    }
}
