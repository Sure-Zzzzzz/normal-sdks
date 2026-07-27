package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
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
 * Management 窄路径安全配置。
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX,
        name = SimpleKafkaOutboxManagementConstant.CONFIG_PROPERTY_UI_ENABLE, havingValue = "true", matchIfMissing = true)
public class SimpleKafkaOutboxManagementSecurityConfiguration {
    private final SimpleKafkaOutboxManagementProperties properties;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建 Management 专用管理员。
     */
    @Bean(name = SimpleKafkaOutboxManagementConstant.BEAN_USER_DETAILS_SERVICE)
    public UserDetailsService simpleKafkaOutboxManagementUserDetailsService() {
        UserDetails admin = User.builder().username(properties.getAdmin().getUsername())
                .password(passwordEncoder.encode(properties.getAdmin().getPassword()))
                .roles(SimpleKafkaOutboxManagementConstant.ADMIN_ROLE).build();
        return new InMemoryUserDetailsManager(admin);
    }

    /**
     * 创建仅覆盖 Management 页面路径的安全链。
     */
    @Bean
    @Order(SimpleKafkaOutboxManagementConstant.DEFAULT_SECURITY_ORDER)
    public SecurityFilterChain simpleKafkaOutboxManagementSecurityFilterChain(HttpSecurity http,
                                                                              @org.springframework.beans.factory.annotation.Qualifier(SimpleKafkaOutboxManagementConstant.BEAN_USER_DETAILS_SERVICE) UserDetailsService userDetailsService) throws Exception {
        String basePath = properties.getUi().getBasePath();
        http.antMatcher(basePath + SimpleKafkaOutboxManagementConstant.PATH_WILDCARD_SUFFIX)
                .userDetailsService(userDetailsService).authorizeRequests()
                .antMatchers(basePath + SimpleKafkaOutboxManagementConstant.PATH_ASSETS_WILDCARD).permitAll()
                .antMatchers(basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGIN).permitAll()
                .anyRequest().authenticated().and().formLogin()
                .loginPage(basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGIN)
                .loginProcessingUrl(basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGIN)
                .defaultSuccessUrl(basePath + "/", true).permitAll().and().logout()
                .logoutUrl(basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGOUT)
                .logoutSuccessUrl(basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGIN + "?logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID").clearAuthentication(true).permitAll();
        return http.build();
    }
}
