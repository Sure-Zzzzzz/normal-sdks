package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.AkskResourceIntrospectionClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.*;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.util.Map;

/**
 * AKSK资源认证适配器。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class AkskResourceAuthenticationAdapter implements ResourceAuthenticationAdapter {

    private static final ResourceAuthenticationSourceId SOURCE_ID = new ResourceAuthenticationSourceId(
            AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);

    private final OpaqueTokenIntrospector introspector;

    /**
     * 创建AKSK资源认证适配器。
     *
     * @param introspector 已认证的AKSK令牌内省器
     */
    public AkskResourceAuthenticationAdapter(OpaqueTokenIntrospector introspector) {
        if (introspector == null) {
            throw new SimpleAkskResourceServerConfigurationException("AKSK内省器不能为null");
        }
        this.introspector = introspector;
    }

    @Override
    public ResourceAuthenticationSourceId sourceId() {
        return SOURCE_ID;
    }

    @Override
    public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
        if (!(credential instanceof BearerResourceCredential)
                || !SOURCE_ID.equals(credential.getSourceId())) {
            log.debug("AKSK资源认证凭据类型或来源不匹配");
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED);
        }
        try {
            OAuth2AuthenticatedPrincipal principal = introspector.introspect(
                    ((BearerResourceCredential) credential).getToken());
            if (principal == null || !isActive(principal.getAttributes())) {
                log.debug("AKSK资源认证内省结果未激活");
                return ResourceAuthenticationResult.rejected(
                        ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
            }
            return authenticated(principal.getAttributes());
        } catch (
                org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException exception) {
            log.warn("AKSK资源认证内省端点不可用，认证拒绝，异常类型={}",
                    exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE);
        } catch (RuntimeException exception) {
            log.warn("AKSK资源认证授权快照处理失败，认证拒绝，异常类型={}",
                    exception.getClass().getName());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
    }

    private boolean isActive(Map<String, Object> claims) {
        return claims != null && Boolean.TRUE.equals(
                claims.get(AkskResourceIntrospectionClaimConstant.ACTIVE));
    }

    private ResourceAuthenticationResult authenticated(Map<String, Object> claims) {
        Object clientIdClaim = claims.get(JwtClaimConstant.CLIENT_ID);
        if (!(clientIdClaim instanceof String)) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.SUBJECT_INVALID);
        }
        ApplicationAuthorizationContext authorization;
        try {
            authorization = ApplicationAuthorizationContextClaimMapper.fromClaim(
                    claims.get(JwtClaimConstant.APPLICATION_AUTHORIZATION));
        } catch (RuntimeException exception) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        String clientId = (String) clientIdClaim;
        if (authorization.getSubjectType() != ApplicationAuthorizationSubjectType.SERVICE
                || !clientId.equals(authorization.getSubjectId())) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                SOURCE_ID, ResourceSubjectType.SERVICE, clientId), authorization);
    }
}
