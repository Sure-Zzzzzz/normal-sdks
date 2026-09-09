package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceTokenVerificationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamResourceVerificationClientEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.TokenVerifiedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.Map;

/**
 * IAM 资源令牌受控验证服务。
 *
 * <p>每次验证终审后发布 {@link TokenVerifiedEvent}；事件携带调用方验证客户端标识
 * 与终审结论，发布失败不影响验证结果。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamResourceTokenVerificationService {

    private final OAuth2AuthorizationService authorizationService;
    private final RegisteredClientRepository registeredClientRepository;
    private final IamUserRepository userRepository;
    private final SessionService sessionService;
    private final IamApplicationAuthorizationService applicationAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 验证令牌并返回最小资源授权信息。
     */
    public ResourceTokenVerificationResponse verify(IamResourceVerificationClientEntity verificationClient,
                                                    String token) {
        ResourceTokenVerificationResponse response = doVerify(verificationClient, token);
        publishVerifiedEvent(verificationClient, token, response != null);
        return response;
    }

    private ResourceTokenVerificationResponse doVerify(IamResourceVerificationClientEntity verificationClient,
                                                       String token) {
        if (verificationClient == null || token == null || token.trim().isEmpty()) {
            return null;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(
                token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null
                || authorization.getAccessToken().isInvalidated()) {
            return null;
        }
        OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
        if (accessToken == null || !token.equals(accessToken.getTokenValue())
                || accessToken.getExpiresAt() == null
                || !accessToken.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        RegisteredClient oauthClient = registeredClientRepository.findById(
                authorization.getRegisteredClientId());
        if (oauthClient == null) {
            return null;
        }
        Long tokenApplicationId = applicationAuthorizationService
                .findApplicationIdByOAuthClientId(oauthClient.getClientId());
        if (tokenApplicationId == null
                || !tokenApplicationId.equals(verificationClient.getApplicationId())) {
            return null;
        }
        String iamSessionId = authorization.getAttribute(
                SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID);
        IamSessionEntity session = sessionService.findActiveById(iamSessionId);
        if (session == null) {
            return null;
        }
        IamUserEntity user = userRepository.findByUsername(authorization.getPrincipalName())
                .orElse(null);
        if (user == null || user.getStatus() == null
                || SimpleIamServerConstant.STATUS_ACTIVE != user.getStatus().intValue()
                || !user.getId().equals(session.getUserId())) {
            return null;
        }
        Instant issuedAt = accessToken.getIssuedAt();
        if (issuedAt == null) {
            return null;
        }
        ApplicationAuthorizationContext context = applicationAuthorizationService.loadActiveContext(
                user.getId(), tokenApplicationId, issuedAt, accessToken.getExpiresAt());
        if (context == null) {
            return null;
        }
        return ResourceTokenVerificationResponse.builder()
                .sub(String.valueOf(user.getId()))
                .iamAuthorization(ApplicationAuthorizationContextClaimMapper.toClaim(context))
                .build();
    }

    /**
     * 发布验证终审事件；授权记录可定位时补齐元数据，否则仅携带 token 原文与结论。
     */
    private void publishVerifiedEvent(IamResourceVerificationClientEntity verificationClient,
                                      String token, boolean active) {
        try {
            OAuth2Authorization authorization = token == null ? null
                    : authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization == null ? null
                    : authorization.getToken(OAuth2AccessToken.class);
            String clientId = null;
            Map<String, Object> claims = accessToken == null ? null : accessToken.getClaims();
            if (authorization != null && authorization.getRegisteredClientId() != null) {
                RegisteredClient registeredClient = registeredClientRepository.findById(
                        authorization.getRegisteredClientId());
                clientId = registeredClient != null ? registeredClient.getClientId() : null;
            }
            eventPublisher.publishEvent(new TokenVerifiedEvent(
                    this, clientId, null,
                    claims == null ? null : String.valueOf(claims.get("sub")),
                    authorization == null ? null : authorization.getPrincipalName(),
                    token,
                    authorization == null ? null : authorization.getAuthorizedScopes(),
                    accessToken == null ? null : accessToken.getToken().getIssuedAt(),
                    accessToken == null ? null : accessToken.getToken().getExpiresAt(),
                    active,
                    verificationClient == null ? null : verificationClient.getClientId()));
        } catch (Exception exception) {
            log.warn("Failed to publish token verified event", exception);
        }
    }

}
