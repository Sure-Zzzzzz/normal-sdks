package io.github.surezzzzzz.sdk.ops.middleware.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * LDAP 端到端测试专用 Spring Security 配置。
 *
 * @author surezzzzzz
 */
@TestConfiguration
@EnableWebSecurity
public class MiddlewareOpsLdapEndToEndSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final AuthenticationProvider ldapAuthenticationProvider;

    /**
     * 创建 LDAP 测试安全配置。
     *
     * @param ldapAuthenticationProvider LDAP bind 认证提供器
     */
    @Autowired
    public MiddlewareOpsLdapEndToEndSecurityConfiguration(AuthenticationProvider ldapAuthenticationProvider) {
        this.ldapAuthenticationProvider = ldapAuthenticationProvider;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(ldapAuthenticationProvider);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable().authorizeRequests().anyRequest().authenticated().and().httpBasic();
    }
}
