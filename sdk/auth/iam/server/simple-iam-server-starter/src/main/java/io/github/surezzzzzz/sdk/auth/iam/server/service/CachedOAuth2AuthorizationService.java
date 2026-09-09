package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * OAuth2 Authorization 缓存装饰器
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class CachedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final SmartCacheManager cacheManager;
    private final int ttlSeconds;
    private final SessionService sessionService;
    private final IamAuthorizeContextService authorizeContextService;

    /**
     * 保存授权并刷新缓存
     *
     * @param authorization OAuth2 授权
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization authorizationToSave = bindCurrentIamSession(authorization);
        delegate.save(authorizationToSave);
        synchronizeAuthorizeContext(authorizationToSave);
        putAuthorization(authorizationToSave);
    }

    private OAuth2Authorization bindCurrentIamSession(OAuth2Authorization authorization) {
        if (authorization.getAttribute(SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID) != null) {
            return authorization;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return authorization;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpSession servletSession = request.getSession(false);
        IamSessionEntity iamSession = servletSession == null ? null : sessionService.getActiveBinding(servletSession);
        if (iamSession == null || !iamSession.getUsername().equals(authorization.getPrincipalName())) {
            return authorization;
        }
        return OAuth2Authorization.from(authorization)
                .attribute(SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID, iamSession.getId())
                .build();
    }

    private void synchronizeAuthorizeContext(OAuth2Authorization authorization) {
        String iamSessionId = authorization.getAttribute(SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID);
        if (iamSessionId == null) {
            return;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpSession servletSession = attributes.getRequest().getSession(false);
        IamSessionEntity iamSession = servletSession == null ? null : sessionService.getActiveBinding(servletSession);
        if (iamSession == null || !iamSessionId.equals(iamSession.getId())
                || !iamSession.getUsername().equals(authorization.getPrincipalName())) {
            return;
        }
        OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(
                OAuth2AuthorizationRequest.class.getName());
        if (authorizationRequest == null) {
            return;
        }
        boolean requireConsent = authorization.getToken(OAuth2AuthorizationCode.class) == null;
        Object codeChallenge = authorizationRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE);
        Object codeChallengeMethod = authorizationRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD);
        Object nonce = authorizationRequest.getAdditionalParameters().get(OidcParameterNames.NONCE);
        authorizeContextService.synchronizeAuthenticatedAuthorization(
                authorization.getId(), authorization.getRegisteredClientId(), authorizationRequest.getClientId(),
                authorizationRequest.getRedirectUri(), authorizationRequest.getScopes(), authorizationRequest.getState(),
                nonce instanceof String ? (String) nonce : null,
                codeChallenge instanceof String ? (String) codeChallenge : null,
                codeChallengeMethod instanceof String ? (String) codeChallengeMethod : null,
                iamSession.getUserId(), iamSession.getId(), servletSession.getId(), requireConsent);
    }

    /**
     * 删除授权并清理缓存
     *
     * @param authorization OAuth2 授权
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        evictAuthorization(authorization);
    }

    /**
     * 按授权 ID 查询
     *
     * @param id 授权 ID
     * @return OAuth2 授权
     */
    @Override
    @Nullable
    public OAuth2Authorization findById(String id) {
        OAuth2Authorization cached = cacheManager.get(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                buildIdKey(id));
        if (cached != null) {
            return cached;
        }
        OAuth2Authorization loaded = delegate.findById(id);
        if (loaded != null) {
            putAuthorization(loaded);
        }
        return loaded;
    }

    /**
     * 按 token 查询
     *
     * @param token     token 原文
     * @param tokenType token 类型
     * @return OAuth2 授权
     */
    @Override
    @Nullable
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        String tokenKey = buildTokenKey(token, tokenType);
        OAuth2Authorization cached = cacheManager.get(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION, tokenKey);
        if (cached != null) {
            return isBoundSessionActive(cached, tokenType) ? cached : null;
        }
        OAuth2Authorization loaded = delegate.findByToken(token, tokenType);
        if (loaded != null) {
            putAuthorization(loaded);
        }
        return isBoundSessionActive(loaded, tokenType) ? loaded : null;
    }

    private boolean isBoundSessionActive(OAuth2Authorization authorization, @Nullable OAuth2TokenType tokenType) {
        if (authorization == null || tokenType == null
                || (!OAuth2ParameterNames.CODE.equals(tokenType.getValue())
                && !OAuth2ParameterNames.STATE.equals(tokenType.getValue())
                && !OAuth2TokenType.REFRESH_TOKEN.getValue().equals(tokenType.getValue()))) {
            return authorization != null;
        }
        String iamSessionId = authorization.getAttribute(SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID);
        return iamSessionId == null || sessionService.findActiveById(iamSessionId) != null;
    }

    private void putAuthorization(OAuth2Authorization authorization) {
        cacheManager.put(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                buildIdKey(authorization.getId()), authorization, ttlSeconds);
        putTokenIndex(authorization, authorization.getAccessToken());
        putTokenIndex(authorization, authorization.getRefreshToken());
        putTokenIndex(authorization, authorization.getToken(OAuth2AuthorizationCode.class));
    }

    private void putTokenIndex(OAuth2Authorization authorization, OAuth2Authorization.Token<?> token) {
        if (token == null || token.getToken() == null) {
            return;
        }
        String tokenValue = token.getToken().getTokenValue();
        if (token.getToken() instanceof OAuth2AccessToken) {
            cacheManager.put(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                    buildTokenKey(tokenValue, OAuth2TokenType.ACCESS_TOKEN), authorization, ttlSeconds);
            return;
        }
        if (token.getToken() instanceof OAuth2RefreshToken) {
            cacheManager.put(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                    buildTokenKey(tokenValue, OAuth2TokenType.REFRESH_TOKEN), authorization, ttlSeconds);
            return;
        }
        if (token.getToken() instanceof OAuth2AuthorizationCode) {
            cacheManager.put(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                    buildTokenKey(tokenValue, new OAuth2TokenType(OAuth2ParameterNames.CODE)), authorization, ttlSeconds);
        }
    }

    private void evictAuthorization(OAuth2Authorization authorization) {
        cacheManager.evict(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION, buildIdKey(authorization.getId()));
        evictTokenIndex(authorization.getAccessToken());
        evictTokenIndex(authorization.getRefreshToken());
        evictTokenIndex(authorization.getToken(OAuth2AuthorizationCode.class));
    }

    private void evictTokenIndex(OAuth2Authorization.Token<?> token) {
        if (token == null || token.getToken() == null) {
            return;
        }
        String tokenValue = token.getToken().getTokenValue();
        if (token.getToken() instanceof OAuth2AccessToken) {
            cacheManager.evict(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                    buildTokenKey(tokenValue, OAuth2TokenType.ACCESS_TOKEN));
            return;
        }
        if (token.getToken() instanceof OAuth2RefreshToken) {
            cacheManager.evict(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                    buildTokenKey(tokenValue, OAuth2TokenType.REFRESH_TOKEN));
            return;
        }
        if (token.getToken() instanceof OAuth2AuthorizationCode) {
            cacheManager.evict(SimpleIamServerConstant.CACHE_OAUTH2_AUTHORIZATION,
                    buildTokenKey(tokenValue, new OAuth2TokenType(OAuth2ParameterNames.CODE)));
        }
    }

    private String buildIdKey(String id) {
        return String.format(SimpleIamServerConstant.CACHE_KEY_OAUTH2_AUTHORIZATION_ID, id);
    }

    private String buildTokenKey(String token, @Nullable OAuth2TokenType tokenType) {
        String type = tokenType == null ? SimpleIamServerConstant.CACHE_TOKEN_TYPE_UNKNOWN : tokenType.getValue();
        return String.format(SimpleIamServerConstant.CACHE_KEY_OAUTH2_AUTHORIZATION_TOKEN,
                type, TokenHashHelper.sha256Hex(token));
    }
}
