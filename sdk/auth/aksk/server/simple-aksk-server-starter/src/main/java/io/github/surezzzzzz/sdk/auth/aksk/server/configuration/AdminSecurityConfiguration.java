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
                        .antMatchers("/admin/css/**", "/admin/js/**", "/admin/img/**")
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
                        .invalidSessionStrategy((request, response) -> {
                            String requestUri = request.getRequestURI();

                            // 静态资源和登录页直接放行，不处理 session
                            if (requestUri.startsWith("/admin/css/") ||
                                    requestUri.startsWith("/admin/js/") ||
                                    requestUri.startsWith("/admin/img/") ||
                                    requestUri.equals("/admin/login")) {
                                return;
                            }

                            // 检查是否是浏览器访问
                            String acceptHeader = request.getHeader("Accept");
                            boolean isBrowserRequest = acceptHeader != null &&
                                    acceptHeader.contains("text/html");

                            if (isBrowserRequest) {
                                // 浏览器访问：清除失效的 JSESSIONID cookie，再重定向到登录页
                                javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("JSESSIONID", "");
                                cookie.setMaxAge(0);
                                cookie.setPath("/");
                                response.addCookie(cookie);
                                response.sendRedirect("/admin/login");
                            } else {
                                // AJAX 访问：返回 401，前端 fetch 拦截器会处理跳转
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
