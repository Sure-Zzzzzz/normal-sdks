package io.github.surezzzzzz.sdk.ops.middleware.configuration;

import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsApiAuthenticationEntryPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Middleware Ops API 安全配置。
 *
 * @author surezzzzzz
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MiddlewareOpsApiSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final AuthenticationProvider authenticationProvider;
    private final SmartMiddlewareOpsServerProperties properties;

    /**
     * 创建 API 安全配置。
     *
     * @param properties Server 配置
     */
    public MiddlewareOpsApiSecurityConfiguration(AuthenticationProvider authenticationProvider,
                                                 SmartMiddlewareOpsServerProperties properties) {
        this.authenticationProvider = authenticationProvider;
        this.properties = properties;
    }

    /**
     * 配置仅作用于 Ops API 的共享会话认证边界。
     *
     * @param http HTTP 安全构建器
     * @throws Exception 配置失败
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        String apiBasePath = properties.getApiBasePath();
        http.requestMatcher(new OrRequestMatcher(new AntPathRequestMatcher(apiBasePath),
                        new AntPathRequestMatcher(apiBasePath + "/**"))).authenticationProvider(authenticationProvider)
                .authorizeRequests().anyRequest().authenticated().and()
                .httpBasic().authenticationEntryPoint(new MiddlewareOpsApiAuthenticationEntryPoint()).and()
                .exceptionHandling().authenticationEntryPoint(new MiddlewareOpsApiAuthenticationEntryPoint()).and()
                .sessionManagement().sessionFixation().migrateSession();
    }
}
