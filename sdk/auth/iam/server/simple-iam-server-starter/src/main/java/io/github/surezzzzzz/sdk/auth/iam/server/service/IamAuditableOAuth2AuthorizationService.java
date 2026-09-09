package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.iam.server.event.TokenIssuedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.event.TokenRemovedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.event.TokenRevokedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 可审计的 IAM OAuth2 授权服务包装器。
 *
 * <p>包装 {@link OAuth2AuthorizationService} 并发布 Token 生命周期事件
 * （颁发 / 撤销 / 删除）；是否持久化或可靠消费由未来的审计处理器负责。
 * 事件发布失败只记告警，不影响授权主流程。
 *
 * <p>IAM 字段口径：principalName 即用户名；userId 取 access token claims 的
 * {@code sub}；clientId 经注册客户端仓库解析；clientType 当前 claim 未携带，预埋为空。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class IamAuditableOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final ApplicationEventPublisher eventPublisher;
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 落库授权并在 token 签发 / 授权码消费节点发布 Token 族审计事件
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        // 授权码流程授权行在 code 阶段已落库，token 颁发是同 id 更新——
        // "新增"须按 access token 首次出现判定，否则 ISSUED 事件永不发布
        OAuth2Authorization existing = delegate.findById(authorization.getId());
        boolean isNewAccessToken = existing == null
                || existing.getToken(OAuth2AccessToken.class) == null;

        delegate.save(authorization);

        try {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                    authorization.getToken(OAuth2AccessToken.class);
            if (accessToken == null) {
                return;
            }
            String clientId = resolveClientId(authorization.getRegisteredClientId());
            Map<String, Object> claims = accessToken.getClaims();
            String username = authorization.getPrincipalName();
            String tokenValue = accessToken.getToken().getTokenValue();
            Set<String> scopes = authorization.getAuthorizedScopes();
            Instant issuedAt = accessToken.getToken().getIssuedAt();
            Instant expiresAt = accessToken.getToken().getExpiresAt();

            if (accessToken.isInvalidated()) {
                eventPublisher.publishEvent(new TokenRevokedEvent(
                        this, TokenEventCause.OAUTH2_REVOKE,
                        clientId, null, getClaim(claims, "sub"), username,
                        tokenValue, scopes, issuedAt, expiresAt));
                log.debug("Published TokenRevokedEvent: clientId={}", clientId);
            } else if (isNewAccessToken) {
                eventPublisher.publishEvent(new TokenIssuedEvent(
                        this, clientId, null, getClaim(claims, "sub"), username,
                        tokenValue, scopes, issuedAt, expiresAt));
                log.debug("Published TokenIssuedEvent: clientId={}", clientId);
            }
        } catch (Exception exception) {
            log.warn("Failed to publish token event on save: {}", authorization.getId(), exception);
        }
    }

    /**
     * 删除授权并按撤销来源发布审计事件
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);

        try {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                    authorization.getToken(OAuth2AccessToken.class);
            if (accessToken == null) {
                return;
            }
            eventPublisher.publishEvent(new TokenRemovedEvent(
                    this, resolveClientId(authorization.getRegisteredClientId()),
                    null, getClaim(accessToken.getClaims(), "sub"), authorization.getPrincipalName(),
                    accessToken.getToken().getTokenValue(),
                    authorization.getAuthorizedScopes(),
                    accessToken.getToken().getIssuedAt(),
                    accessToken.getToken().getExpiresAt()));
            log.debug("Published TokenRemovedEvent: clientId={}", authorization.getRegisteredClientId());
        } catch (Exception exception) {
            log.warn("Failed to publish token event on remove: {}", authorization.getId(), exception);
        }
    }

    /**
     * 按 id 读取授权（透传缓存链）
     */
    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    /**
     * 按 token 值读取授权（透传缓存链）
     */
    @Override
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private String resolveClientId(String registeredClientId) {
        if (registeredClientId == null) {
            return null;
        }
        RegisteredClient registeredClient = registeredClientRepository.findById(registeredClientId);
        return registeredClient != null ? registeredClient.getClientId() : null;
    }

    private String getClaim(Map<String, Object> claims, String key) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(key);
        return value != null ? value.toString() : null;
    }
}
