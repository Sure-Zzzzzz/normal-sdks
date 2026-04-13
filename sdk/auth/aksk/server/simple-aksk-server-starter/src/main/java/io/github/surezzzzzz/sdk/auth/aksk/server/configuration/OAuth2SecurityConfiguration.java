package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AnonymousIntrospectionFilter;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.DefaultScopeAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.JwtKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Security Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OAuth2SecurityConfiguration {

    private final JwtKeyProvider jwtKeyProvider;
    private final OAuth2RegisteredClientEntityRepository entityRepository;
    private final SimpleAkskServerProperties properties;

    @Autowired(required = false)
    private OAuth2AuthorizationService authorizationService;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint.accessTokenRequestConverter(
                                new DefaultScopeAuthenticationConverter(entityRepository)
                        )
                );

        // requireAuthentication=false 时，在 OAuth2ClientAuthenticationFilter 之前
        // 加一个 filter 直接处理无认证的 introspect 请求
        if (!properties.getIntrospect().isRequireAuthentication()) {
            log.warn("⚠️  introspect.require-authentication=false: /oauth2/introspect 端点无需认证即可访问，" +
                    "仅适用于网络隔离的内网/测试环境，生产环境请勿使用此配置！");
            if (authorizationService != null) {
                // 放在 LogoutFilter 之后，UsernamePasswordAuthenticationFilter 之前
                http.addFilterAfter(
                        new AnonymousIntrospectionFilter(authorizationService),
                        org.springframework.security.web.authentication.logout.LogoutFilter.class
                );
            }
        }

        http.httpBasic().disable();
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher("/api/**")
                .authorizeRequests(authorizeRequests ->
                        authorizeRequests.anyRequest().authenticated()
                )
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
                .csrf().disable();
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests(authorizeRequests ->
                        authorizeRequests.anyRequest().permitAll()
                )
                .formLogin().disable()
                .httpBasic().disable()
                .csrf().disable();
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(jwtKeyProvider.getPublicKey()).build();
    }
}
