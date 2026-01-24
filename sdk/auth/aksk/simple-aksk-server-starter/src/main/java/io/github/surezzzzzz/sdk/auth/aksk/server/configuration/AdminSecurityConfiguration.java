package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletContextInitializer;
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

import javax.servlet.http.HttpServletResponse;

/**
 * Admin Security Configuration
 * <p>
 * Admin管理页面相关的安全配置
 * 当 admin.enabled=false 时，此配置类不会被加载
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.auth.aksk.server.admin",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AdminSecurityConfiguration {

    private final SimpleAkskServerProperties properties;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Order(3)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher("/admin/**")
                .authorizeRequests(authorize -> authorize
                        .antMatchers("/admin/css/**", "/admin/js/**")
                        .permitAll()
                        .antMatchers("/admin/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/admin/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .clearAuthentication(true)
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/admin/login")
                        .and()
                        .invalidSessionStrategy((request, response) -> {
                            // 静态资源(CSS/JS)不需要session,直接放行
                            String requestUri = request.getRequestURI();
                            if (requestUri.startsWith("/admin/css/") ||
                                    requestUri.startsWith("/admin/js/")) {
                                // 静态资源放行,不做任何处理
                                return;
                            }

                            // 登录页面允许无session访问,继续处理请求
                            if (requestUri.equals("/admin/login")) {
                                // 清除无效session cookie,避免重复触发
                                request.getSession(false); // 不创建新session
                                return; // 让请求继续到Controller
                            }

                            // 检查是否是浏览器访问(通过Accept header判断)
                            String acceptHeader = request.getHeader("Accept");
                            boolean isBrowserRequest = acceptHeader != null &&
                                    acceptHeader.contains("text/html");

                            if (isBrowserRequest) {
                                // 浏览器访问:重定向到登录页
                                response.sendRedirect("/admin/login");
                            } else {
                                // API访问(包括测试):返回401
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            }
                        })
                )
                .httpBasic().disable();
        // CSRF保护默认启用,不需要显式配置
        return http.build();
    }

    @Bean
    public ServletContextInitializer sessionTimeoutInitializer() {
        return servletContext -> servletContext.setSessionTimeout(
                properties.getAdmin().getSessionTimeoutMinutes()
        );
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username(properties.getAdmin().getUsername())
                .password(passwordEncoder.encode(properties.getAdmin().getPassword()))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }
}
