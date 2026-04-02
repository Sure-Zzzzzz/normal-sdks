package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 测试用的Security配置
 *
 * <p>允许所有请求通过，不拦截
 * <p>只在Header认证测试中启用，JWT测试不使用此配置
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@TestConfiguration
@EnableWebSecurity
@ConditionalOnProperty(
        prefix = "test.security",
        name = "permit-all",
        havingValue = "true"
)
public class TestSecurityConfig {

    @Bean
    @Order(100)  // 最低优先级
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .anyRequest().permitAll()
                .and()
                .csrf().disable();
        return http.build();
    }
}
