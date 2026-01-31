package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.DefaultScopeAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.JwtKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Security Configuration
 * 配置OAuth2授权服务器的安全链、API安全链、密码编码器、JWT解码器
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2SecurityConfiguration {

    private final JwtKeyProvider jwtKeyProvider;
    private final OAuth2RegisteredClientEntityRepository entityRepository;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // 应用默认的OAuth2授权服务器安全配置
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        // 获取OAuth2AuthorizationServerConfigurer并自定义token endpoint
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint.accessTokenRequestConverter(
                                new DefaultScopeAuthenticationConverter(entityRepository)
                        )
                );

        // 禁用httpBasic,避免弹出浏览器登录框
        http.httpBasic().disable();

        return http.build();
    }

    /**
     * API Security Filter Chain
     * 要求JWT Token认证
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher("/api/**")
                .authorizeRequests(authorizeRequests ->
                        authorizeRequests.anyRequest().authenticated()  // 需要认证
                )
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt  // 使用JWT验证
                )
                .csrf().disable();
        return http.build();
    }

    /**
     * Default Security Filter Chain
     * 只处理OAuth2授权服务器和API之外的请求,不拦截admin路径
     */
    @Bean
    @Order(4)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests(authorizeRequests ->
                        authorizeRequests.anyRequest().permitAll()  // 放行其他所有请求
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

    /**
     * JWT Decoder - 用于验证JWT Token
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(jwtKeyProvider.getPublicKey()).build();
    }
}
