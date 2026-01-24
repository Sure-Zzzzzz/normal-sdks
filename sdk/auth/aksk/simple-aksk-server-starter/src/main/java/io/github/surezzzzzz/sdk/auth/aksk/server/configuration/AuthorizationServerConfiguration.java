package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.EnabledAwareRegisteredClientRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2AuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.JwtKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/**
 * Authorization Server Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfiguration {

    private final JwtKeyProvider jwtKeyProvider;
    private final JdbcTemplate jdbcTemplate;
    private final SimpleAkskServerProperties properties;
    private final OAuth2RegisteredClientEntityRepository entityRepository;

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = new RSAKey.Builder(jwtKeyProvider.getPublicKey())
                .privateKey(jwtKeyProvider.getPrivateKey())
                .keyID(properties.getJwt().getKeyId())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        JdbcRegisteredClientRepository jdbcRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
        return new EnabledAwareRegisteredClientRepository(jdbcRepository, entityRepository);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        JdbcOAuth2AuthorizationService jdbcService = new JdbcOAuth2AuthorizationService(
                jdbcTemplate,
                registeredClientRepository()
        );

        // 如果启用了Redis，使用缓存包装
        if (properties.getRedis().getEnabled()) {
            log.info("Redis caching enabled for OAuth2 authorization storage");
            return new CachedOAuth2AuthorizationService(jdbcService);
        }

        log.info("Using JDBC-only storage for OAuth2 authorization");
        return jdbcService;
    }
}
