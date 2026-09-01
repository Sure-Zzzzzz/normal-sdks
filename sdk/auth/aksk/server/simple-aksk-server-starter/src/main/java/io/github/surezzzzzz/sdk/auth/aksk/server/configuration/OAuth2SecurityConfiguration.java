package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.converter.DefaultScopeAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.server.filter.AkskServerOAuth2LimiterFilter;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskApplicationAuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskIntrospectionResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * OAuth2 Security Configuration
 * <p>
 * /api/** 由公共资源层链（simple-resource-server-starter，HIGHEST_PRECEDENCE）接管；
 * 本类只保留授权服务器链与 default 链。
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2SecurityConfiguration {

    private final CachedOAuth2RegisteredClientEntityService cachedClientEntityService;
    private final AkskApplicationAuthorizationService applicationAuthorizationService;
    private final ObjectProvider<AkskServerOAuth2LimiterFilter> akskServerOAuth2LimiterFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint.accessTokenRequestConverter(
                                new DefaultScopeAuthenticationConverter(cachedClientEntityService)
                        )
                )
                .tokenIntrospectionEndpoint(tokenIntrospectionEndpoint ->
                        tokenIntrospectionEndpoint.introspectionResponseHandler(
                                new AkskIntrospectionResponseHandler(applicationAuthorizationService)
                        )
                );

        akskServerOAuth2LimiterFilter.ifAvailable(filter ->
                http.addFilterBefore(filter, AbstractPreAuthenticatedProcessingFilter.class));

        http.httpBasic().disable();
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
}
