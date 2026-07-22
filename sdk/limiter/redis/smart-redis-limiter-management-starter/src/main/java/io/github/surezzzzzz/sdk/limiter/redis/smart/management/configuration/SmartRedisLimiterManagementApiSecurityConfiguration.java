package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Management API 窄路径安全配置
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".api",
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
public class SmartRedisLimiterManagementApiSecurityConfiguration {

    private final SmartRedisLimiterManagementProperties properties;

    /**
     * admin REST 窄路径安全链，仅覆盖 api.base-path/admin/**
     */
    @Bean
    @Order(SmartRedisLimiterManagementConstant.DEFAULT_SECURITY_ORDER - 1)
    public SecurityFilterChain managementApiSecurityFilterChain(HttpSecurity http) throws Exception {
        String adminBasePath = properties.getApi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_ADMIN_PREFIX
                + SmartRedisLimiterManagementConstant.PATH_WILDCARD_SUFFIX;
        http
                .antMatcher(adminBasePath)
                .authorizeRequests()
                .anyRequest()
                .authenticated()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED));
        return http.build();
    }
}
