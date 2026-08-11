package io.github.surezzzzzz.sdk.ops.middleware.configuration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Middleware Ops 页面安全配置。
 *
 * @author surezzzzzz
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MiddlewareOpsUiSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final AuthenticationProvider authenticationProvider;
    private final SmartMiddlewareOpsServerProperties properties;

    /**
     * 创建页面安全配置。
     *
     * @param properties Server 配置
     */
    public MiddlewareOpsUiSecurityConfiguration(AuthenticationProvider authenticationProvider,
                                                SmartMiddlewareOpsServerProperties properties) {
        this.authenticationProvider = authenticationProvider;
        this.properties = properties;
    }

    /**
     * 配置页面表单登录、会话失效与退出边界。
     *
     * @param http HTTP 安全构建器
     * @throws Exception 配置失败
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        String uiBasePath = properties.getUiBasePath();
        String loginPath = uiBasePath + "/login";
        http.requestMatcher(new OrRequestMatcher(new AntPathRequestMatcher("/"), new AntPathRequestMatcher("/favicon.ico"),
                        new AntPathRequestMatcher(uiBasePath), new AntPathRequestMatcher(uiBasePath + "/**")))
                .authenticationProvider(authenticationProvider).authorizeRequests()
                .antMatchers("/", "/favicon.ico", loginPath, uiBasePath + "/console.css", uiBasePath + "/console.js",
                        uiBasePath + "/favicon.svg").permitAll()
                .anyRequest().authenticated().and().exceptionHandling()
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(loginPath)).and().formLogin()
                .loginPage(loginPath).loginProcessingUrl(loginPath).failureUrl(loginPath + "?error")
                .defaultSuccessUrl(uiBasePath, true).permitAll().and().httpBasic().disable().logout()
                .logoutUrl(uiBasePath + "/logout").logoutSuccessUrl(loginPath).invalidateHttpSession(true)
                .clearAuthentication(true).and().sessionManagement().sessionFixation().migrateSession();
    }
}
