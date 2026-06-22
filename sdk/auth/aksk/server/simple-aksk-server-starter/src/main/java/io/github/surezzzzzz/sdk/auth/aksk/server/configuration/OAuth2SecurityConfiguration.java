package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.converter.DefaultScopeAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.server.filter.AkskServerOAuth2LimiterFilter;
import io.github.surezzzzzz.sdk.auth.aksk.server.filter.AnonymousIntrospectionFilter;
import io.github.surezzzzzz.sdk.auth.aksk.server.provider.JwtKeyProvider;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService;
import io.github.surezzzzzz.sdk.auth.aksk.server.token.JweJwtDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

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
    private final CachedOAuth2RegisteredClientEntityService cachedClientEntityService;
    private final SimpleAkskServerProperties properties;
    private final JweJwtDecoder jweJwtDecoder;
    private final AuthorizationServerSettings authorizationServerSettings;
    private final ObjectProvider<AkskServerOAuth2LimiterFilter> akskServerOAuth2LimiterFilter;
    private final OAuth2AuthorizationService authorizationService;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint.accessTokenRequestConverter(
                                new DefaultScopeAuthenticationConverter(cachedClientEntityService)
                        )
                );

        akskServerOAuth2LimiterFilter.ifAvailable(filter ->
                http.addFilterBefore(filter, AbstractPreAuthenticatedProcessingFilter.class));

        // requireAuthentication=false 时，在 OAuth2ClientAuthenticationFilter 之前
        // 加一个 filter 直接处理无认证的 introspect 请求
        if (!properties.getIntrospect().isRequireAuthentication()) {
            log.warn("⚠️  introspect.require-authentication=false: /oauth2/introspect 端点无需认证即可访问，" +
                    "仅适用于网络隔离的内网/测试环境，生产环境请勿使用此配置！");
            // 放在 LogoutFilter 之后，UsernamePasswordAuthenticationFilter 之前
            http.addFilterAfter(
                    new AnonymousIntrospectionFilter(authorizationService, authorizationServerSettings),
                    org.springframework.security.web.authentication.logout.LogoutFilter.class
            );
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
        return token -> {
            try {
                com.nimbusds.jwt.JWTClaimsSet claims = jweJwtDecoder.decode(token);
                java.time.Instant issuedAt = claims.getIssueTime() != null
                        ? claims.getIssueTime().toInstant() : null;
                java.time.Instant expiresAt = claims.getExpirationTime() != null
                        ? claims.getExpirationTime().toInstant() : null;
                return Jwt.withTokenValue(token)
                        .headers(h -> h.put("alg", "A256GCMKW"))
                        .claims(c -> c.putAll(claims.getClaims()))
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .build();
            } catch (Exception e) {
                throw new InvalidBearerTokenException("Invalid JWE token: " + e.getMessage(), e);
            }
        };
    }
}
