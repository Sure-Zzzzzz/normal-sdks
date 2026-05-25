package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.surezzzzzz.sdk.auth.aksk.server.provider.JwtKeyProvider;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.EnabledAwareRegisteredClientRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AuditableOAuth2AuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2AuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.token.JweOAuth2TokenGenerator;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

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
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private SmartCacheManager smartCacheManager;

    @Autowired(required = false)
    private RedisKeyHelper redisKeyHelper;

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

        OAuth2AuthorizationService service = jdbcService;

        // 可选：SmartCache 缓存层（L1+L2）
        if (properties.getRedis().getEnabled()) {
            if (smartCacheManager != null) {
                log.info("Smart cache (L1+L2) enabled for OAuth2 authorization storage");
            } else {
                log.info("Redis enabled but SmartCacheManager not available, using database only");
            }
            service = new CachedOAuth2AuthorizationService(jdbcService, smartCacheManager, redisKeyHelper);
        }

        // 始终：审计事件层
        service = new AuditableOAuth2AuthorizationService(service, eventPublisher);
        log.info("Auditable OAuth2 authorization service initialized");

        return service;
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> jweOAuth2TokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer) {
        return new JweOAuth2TokenGenerator(properties, jwkSource, tokenCustomizer);
    }
}