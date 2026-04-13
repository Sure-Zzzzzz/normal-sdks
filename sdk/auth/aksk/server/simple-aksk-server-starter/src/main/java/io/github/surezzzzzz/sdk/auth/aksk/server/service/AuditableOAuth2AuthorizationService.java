package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenIntrospectedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenIssuedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRemovedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRevokedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Auditable OAuth2 Authorization Service
 *
 * <p>Wraps any {@link OAuth2AuthorizationService} and publishes token lifecycle events
 * for audit purposes. Always active regardless of Redis configuration.
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class AuditableOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void save(OAuth2Authorization authorization) {
        // 判断是否为新增（颁发）：save 前查一下是否已存在
        boolean isNew = delegate.findById(authorization.getId()) == null;

        delegate.save(authorization);

        try {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                    authorization.getToken(OAuth2AccessToken.class);
            if (accessToken == null) {
                return;
            }

            String tokenValue = accessToken.getToken().getTokenValue();
            Set<String> scopes = authorization.getAuthorizedScopes();
            Instant issuedAt = accessToken.getToken().getIssuedAt();
            Instant expiresAt = accessToken.getToken().getExpiresAt();
            Map<String, Object> claims = accessToken.getClaims();

            String clientId = authorization.getPrincipalName();
            String clientType = getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_CLIENT_TYPE);
            String userId = getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_USER_ID);
            String username = getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_USERNAME);

            if (accessToken.isInvalidated()) {
                eventPublisher.publishEvent(new TokenRevokedEvent(
                        this, clientId, clientType, userId, username,
                        tokenValue, scopes, issuedAt, expiresAt));
                log.debug("Published TokenRevokedEvent: clientId={}", clientId);
            } else if (isNew) {
                eventPublisher.publishEvent(new TokenIssuedEvent(
                        this, clientId, clientType, userId, username,
                        tokenValue, scopes, issuedAt, expiresAt));
                log.debug("Published TokenIssuedEvent: clientId={}", clientId);
            }
        } catch (Exception e) {
            log.warn("Failed to publish token event on save: {}", authorization.getId(), e);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);

        try {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                    authorization.getToken(OAuth2AccessToken.class);
            if (accessToken == null) {
                return;
            }

            Map<String, Object> claims = accessToken.getClaims();
            String clientId = authorization.getPrincipalName();

            eventPublisher.publishEvent(new TokenRemovedEvent(
                    this, clientId,
                    getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_CLIENT_TYPE),
                    getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_USER_ID),
                    getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_USERNAME),
                    accessToken.getToken().getTokenValue(),
                    authorization.getAuthorizedScopes(),
                    accessToken.getToken().getIssuedAt(),
                    accessToken.getToken().getExpiresAt()));
            log.debug("Published TokenRemovedEvent: clientId={}", clientId);
        } catch (Exception e) {
            log.warn("Failed to publish token event on remove: {}", authorization.getId(), e);
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);

        if (authorization != null) {
            try {
                OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                        authorization.getToken(OAuth2AccessToken.class);
                if (accessToken != null) {
                    Map<String, Object> claims = accessToken.getClaims();
                    String clientId = authorization.getPrincipalName();
                    boolean active = !accessToken.isInvalidated()
                            && (accessToken.getToken().getExpiresAt() == null
                            || accessToken.getToken().getExpiresAt().isAfter(Instant.now()));

                    eventPublisher.publishEvent(new TokenIntrospectedEvent(
                            this, clientId,
                            getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_CLIENT_TYPE),
                            getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_USER_ID),
                            getClaim(claims, SimpleAkskServerConstant.JWT_CLAIM_USERNAME),
                            accessToken.getToken().getTokenValue(),
                            authorization.getAuthorizedScopes(),
                            accessToken.getToken().getIssuedAt(),
                            accessToken.getToken().getExpiresAt(),
                            active));
                    log.debug("Published TokenIntrospectedEvent: clientId={}, active={}", clientId, active);
                }
            } catch (Exception e) {
                log.warn("Failed to publish token event on findByToken", e);
            }
        }

        return authorization;
    }

    private String getClaim(Map<String, Object> claims, String key) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(key);
        return value != null ? value.toString() : null;
    }
}
