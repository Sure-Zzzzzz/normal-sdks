package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Management UI 窄路径安全配置
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".ui",
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
public class SmartRedisLimiterManagementSecurityConfiguration {

    private final SmartRedisLimiterManagementProperties properties;
    private final PasswordEncoder passwordEncoder;

    /**
     * Management UI 专用固定管理员（bean 名称唯一，避免与宿主 UserDetailsService 冲突）
     */
    @Bean
    public UserDetailsService smartRedisLimiterManagementUserDetailsService() {
        UserDetails admin = User.builder()
                .username(properties.getAdmin().getUsername())
                .password(passwordEncoder.encode(properties.getAdmin().getPassword()))
                .roles(SmartRedisLimiterManagementConstant.ADMIN_ROLE)
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    /**
     * Management UI 窄路径安全链，仅覆盖 ui.base-path/**
     */
    @Bean
    @Order(SmartRedisLimiterManagementConstant.DEFAULT_SECURITY_ORDER)
    public SecurityFilterChain managementUiSecurityFilterChain(HttpSecurity http) throws Exception {
        String uiBasePath = properties.getUi().getBasePath();
        http
                .antMatcher(uiBasePath + SmartRedisLimiterManagementConstant.PATH_WILDCARD_SUFFIX)
                .userDetailsService(smartRedisLimiterManagementUserDetailsService())
                .authorizeRequests()
                .antMatchers(uiBasePath + SmartRedisLimiterManagementConstant.PATH_ASSETS_WILDCARD)
                .permitAll()
                .antMatchers(uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGIN)
                .permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .formLogin()
                .loginPage(uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGIN)
                .loginProcessingUrl(uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGIN)
                .defaultSuccessUrl(uiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE, true)
                .permitAll()
                .and()
                .logout()
                .logoutUrl(uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGOUT)
                .logoutSuccessUrl(uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGIN
                        + SmartRedisLimiterManagementConstant.QUERY_PARAM_LOGOUT_SUCCESS);
        return http.build();
    }
}
