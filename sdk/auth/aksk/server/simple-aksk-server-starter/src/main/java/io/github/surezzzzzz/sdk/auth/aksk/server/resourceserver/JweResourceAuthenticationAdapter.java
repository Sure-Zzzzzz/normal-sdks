package io.github.surezzzzzz.sdk.auth.aksk.server.resourceserver;

import com.nimbusds.jwt.JWTClaimsSet;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskEffectiveAuthorizationResult;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskEffectiveAuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService;
import io.github.surezzzzzz.sdk.auth.aksk.server.token.JweJwtDecoder;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.*;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;

/**
 * AKSK Server自签JWE资源认证适配器。
 * <p>
 * server 验自签 token 时同时回查本地 OAuth2 授权记录；不能仅凭签名接受已撤销或已过期 token。
 * OWNER_INHERITED AKU 还必须实时读取身份源当前投影，失败关闭。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class JweResourceAuthenticationAdapter implements ResourceAuthenticationAdapter {

    private static final ResourceAuthenticationSourceId SOURCE_ID = new ResourceAuthenticationSourceId(
            AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);

    private final JweJwtDecoder jweJwtDecoder;
    private final OAuth2AuthorizationService authorizationService;
    private final CachedOAuth2RegisteredClientEntityService cachedClientEntityService;
    private final AkskEffectiveAuthorizationService effectiveAuthorizationService;

    @Override
    public ResourceAuthenticationSourceId sourceId() {
        return SOURCE_ID;
    }

    @Override
    public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
        if (!(credential instanceof BearerResourceCredential)
                || !SOURCE_ID.equals(credential.getSourceId())) {
            log.debug("AKSK Server资源认证凭据类型或来源不匹配");
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED);
        }
        JWTClaimsSet claims;
        try {
            claims = jweJwtDecoder.decode(((BearerResourceCredential) credential).getToken());
        } catch (ConfigurationException exception) {
            log.warn("AKSK Server自签JWE解密或验签失败，认证拒绝，异常类型={}", exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(
                    ResourceAuthenticationFailureCategory.SIGNATURE_OR_DECRYPTION_FAILED);
        }
        return authenticated(((BearerResourceCredential) credential).getToken(), claims);
    }

    private ResourceAuthenticationResult authenticated(String tokenValue, JWTClaimsSet claims) {
        Object clientIdClaim = claims.getClaim(JwtClaimConstant.CLIENT_ID);
        if (!(clientIdClaim instanceof String)) {
            log.debug("AKSK Server资源认证拒绝：clientId claim缺失或非字符串");
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.SUBJECT_INVALID);
        }
        String clientId = (String) clientIdClaim;
        ResourceAuthenticationResult tokenState = validateTokenState(tokenValue, clientId, claims);
        if (tokenState != null) {
            log.debug("AKSK Server资源认证拒绝：本地 token 状态无效，clientId={}, category={}",
                    clientId, tokenState.getFailureCategory());
            return tokenState;
        }
        ApplicationAuthorizationContext tokenAuthorization;
        try {
            tokenAuthorization = ApplicationAuthorizationContextClaimMapper.fromClaim(
                    claims.getClaim(JwtClaimConstant.APPLICATION_AUTHORIZATION));
        } catch (RuntimeException exception) {
            log.debug("AKSK Server资源认证拒绝：应用授权claim解析失败，异常={}", exception.getMessage());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        Instant issuedAt = instant(claims.getIssueTime());
        Instant expiresAt = instant(claims.getExpirationTime());
        if (issuedAt == null || expiresAt == null) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
        }
        AkskEffectiveAuthorizationResult effective = effectiveAuthorizationService.resolve(clientId, issuedAt, expiresAt);
        if (effective == null || effective.getAuthorization() == null) {
            log.debug("AKSK Server资源认证拒绝：当前授权投影无效，clientId={}, hasEffective={}, hasAuthorization={}",
                    clientId, effective != null, effective != null && effective.getAuthorization() != null);
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        if (!matchesCurrentAuthorization(claims, tokenAuthorization, clientId, effective)) {
            log.debug("AKSK Server资源认证拒绝：token 快照与当前授权不一致，clientId={}, mode={}",
                    clientId, effective.getAuthorizationMode());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        ApplicationAuthorizationContext currentAuthorization = effective.getAuthorization();
        if (AkskOwnerAuthorizationMode.OWNER_INHERITED.equals(effective.getAuthorizationMode())) {
            log.debug("AKSK Server继承 AKU 资源认证通过：clientId={}, ownerSubjectId={}",
                    clientId, effective.getOwnerSubjectId());
            return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                    SOURCE_ID, ResourceSubjectType.HUMAN, effective.getOwnerSubjectId()), currentAuthorization);
        }
        log.debug("AKSK Server静态资源认证通过：clientId={}", clientId);
        return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                SOURCE_ID, ResourceSubjectType.SERVICE, clientId), currentAuthorization);
    }

    /**
     * 验证原始 bearer 仍对应未撤销、未过期且未被替换的 OAuth2 授权记录。
     */
    private ResourceAuthenticationResult validateTokenState(String tokenValue, String clientId, JWTClaimsSet claims) {
        try {
            OAuth2RegisteredClientEntity client = cachedClientEntityService.findByClientId(clientId).orElse(null);
            if (client == null || !client.isEnabled()) {
                log.debug("AKSK Server资源认证拒绝：client 已删除或禁用，clientId={}, exists={}, enabled={}",
                        clientId, client != null, client != null && client.isEnabled());
                return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
            }
        } catch (RuntimeException exception) {
            log.warn("AKSK Server资源认证读取 client 状态失败，认证拒绝，异常类型={}",
                    exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE);
        }
        OAuth2Authorization authorization;
        try {
            authorization = authorizationService.findByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN);
        } catch (RuntimeException exception) {
            log.warn("AKSK Server资源认证读取 token 状态失败，认证拒绝，异常类型={}",
                    exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE);
        }
        if (authorization == null || !clientId.equals(authorization.getPrincipalName())) {
            log.debug("AKSK Server资源认证拒绝：授权记录不存在或主体不匹配，clientId={}, exists={}, principalMatches={}",
                    clientId, authorization != null,
                    authorization != null && clientId.equals(authorization.getPrincipalName()));
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
        }
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        if (accessToken == null || accessToken.isInvalidated()
                || !tokenValue.equals(accessToken.getToken().getTokenValue())
                || accessToken.getToken().getExpiresAt() == null
                || !accessToken.getToken().getExpiresAt().isAfter(Instant.now())) {
            log.debug("AKSK Server资源认证拒绝：access token 状态不匹配，clientId={}, exists={}, invalidated={}, valueMatches={}, expiresAtPresent={}, active={}",
                    clientId, accessToken != null, accessToken != null && accessToken.isInvalidated(),
                    accessToken != null && tokenValue.equals(accessToken.getToken().getTokenValue()),
                    accessToken != null && accessToken.getToken().getExpiresAt() != null,
                    accessToken != null && accessToken.getToken().getExpiresAt() != null
                            && accessToken.getToken().getExpiresAt().isAfter(Instant.now()));
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
        }
        return null;
    }

    /**
     * 将令牌快照与当前授权结论绑定，避免旧 token 在权限调整后继续以旧主体或旧纪元访问。
     */
    private boolean matchesCurrentAuthorization(JWTClaimsSet claims, ApplicationAuthorizationContext tokenAuthorization,
                                                String clientId, AkskEffectiveAuthorizationResult effective) {
        ApplicationAuthorizationContext currentAuthorization = effective.getAuthorization();
        if (!currentAuthorization.getSubjectType().equals(tokenAuthorization.getSubjectType())
                || !currentAuthorization.getSubjectId().equals(tokenAuthorization.getSubjectId())
                || !currentAuthorization.getApplicationCode().equals(tokenAuthorization.getApplicationCode())) {
            return false;
        }
        if (!AkskOwnerAuthorizationMode.OWNER_INHERITED.equals(effective.getAuthorizationMode())) {
            return isStaticMode(claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_AUTHORIZATION_MODE))
                    && currentAuthorization.getSubjectType() == ApplicationAuthorizationSubjectType.SERVICE
                    && clientId.equals(currentAuthorization.getSubjectId());
        }
        return AkskAuthorizationMode.OWNER_INHERITED.name().equals(
                claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_AUTHORIZATION_MODE))
                && currentAuthorization.getSubjectType() == ApplicationAuthorizationSubjectType.HUMAN
                && effective.getOwnerSubjectId().equals(currentAuthorization.getSubjectId())
                && sameLong(claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_TARGET_APPLICATION_ID),
                effective.getTargetApplicationId())
                && sameLong(claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_OWNER_SECURITY_EPOCH),
                effective.getOwnerSecurityEpoch())
                && sameLong(claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_APPLICATION_AUTHORIZATION_EPOCH),
                effective.getApplicationAuthorizationEpoch())
                && sameLong(claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_OWNER_INHERITED_ACCESS_EPOCH),
                effective.getOwnerInheritedAccessEpoch())
                && sameLong(claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_PROJECTION_ACCESS_EPOCH),
                effective.getProjectionAccessEpoch())
                && effective.getOwnerSourceId().equals(
                claims.getClaim(SimpleAkskServerConstant.JWT_CLAIM_OWNER_SOURCE_ID));
    }

    private boolean isStaticMode(Object mode) {
        return mode == null || AkskAuthorizationMode.STATIC_LEGACY.name().equals(mode);
    }

    private boolean sameLong(Object value, Long expected) {
        return value instanceof Number && expected != null
                && ((Number) value).longValue() == expected.longValue();
    }

    private Instant instant(java.util.Date value) {
        return value == null ? null : value.toInstant();
    }
}
