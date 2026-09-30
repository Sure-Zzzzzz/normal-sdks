package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Boot 3.x（jakarta 线）唯一资源服务安全链。
 *
 * @author surezzzzzz
 */
@Configuration
public class ResourceServerModernSecurityConfiguration {

    /**
     * 创建现代Spring Security资源安全链。
     *
     * @param http           Spring Security配置器
     * @param properties     资源服务配置
     * @param engine         资源认证编排引擎
     * @param eventPublisher 已验证访问事件发布器
     * @param environment    Spring环境
     * @return 资源安全链
     * @throws Exception 配置异常
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http, ResourceServerProperties properties,
                                                                 ResourceServerEngine engine,
                                                                 ApplicationEventPublisher eventPublisher, Environment environment)
            throws Exception {
        ResourceServerSecurityConfigurer.configure(http, properties, engine, eventPublisher, environment);
        return http.build();
    }
}
