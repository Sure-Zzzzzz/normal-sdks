package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskAuthorizationMode;
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
    private final boolean ownerInheritedEnabled;
    private final Long ownerInheritedTargetApplicationId;

    /**
     * 创建AKSK资源认证适配器。
     *
     * @param introspector 已认证的AKSK令牌内省器
     */
    public AkskResourceAuthenticationAdapter(OpaqueTokenIntrospector introspector) {
        this(introspector, false, null);
    }

    /**
     * 创建指定 inherited 目标应用的认证适配器。
     *
     * @param ownerInheritedEnabled 是否允许接收 inherited AKU；只能与严格在线模式一起启用
     * @param ownerInheritedTargetApplicationId 本应用 IAM ID，开启 inherited AKU 时必填
     */
    public AkskResourceAuthenticationAdapter(OpaqueTokenIntrospector introspector, boolean ownerInheritedEnabled,
                                             Long ownerInheritedTargetApplicationId) {
        if (introspector == null) {
            throw new SimpleAkskResourceServerConfigurationException("AKSK内省器不能为null");
        }
        if (ownerInheritedEnabled && (ownerInheritedTargetApplicationId == null
                || ownerInheritedTargetApplicationId.longValue() <= 0L)) {
            throw new SimpleAkskResourceServerConfigurationException("inherited AKU目标应用ID必须为正数");
        }
        this.introspector = introspector;
        this.ownerInheritedEnabled = ownerInheritedEnabled;
        this.ownerInheritedTargetApplicationId = ownerInheritedTargetApplicationId;
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
        Object mode = claims.get(JwtClaimConstant.AUTHORIZATION_MODE);
        if (AkskAuthorizationMode.OWNER_INHERITED.name().equals(mode)) {
            if (!ownerInheritedEnabled || authorization.getSubjectType() != ApplicationAuthorizationSubjectType.HUMAN) {
                return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
            }
            if (!ownerInheritedTargetApplicationId.equals(numberClaim(
                    claims.get(JwtClaimConstant.TARGET_APPLICATION_ID)))) {
                return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
            }
            return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                    SOURCE_ID, ResourceSubjectType.HUMAN, authorization.getSubjectId()), authorization);
        }
        // 旧 Token 不带模式；3.2 起静态 AKP/AKU 显式标记 STATIC_LEGACY，两者都属于服务主体链路。
        if (mode != null && !AkskAuthorizationMode.STATIC_LEGACY.name().equals(mode)
                || authorization.getSubjectType() != ApplicationAuthorizationSubjectType.SERVICE
                || !clientId.equals(authorization.getSubjectId())) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                SOURCE_ID, ResourceSubjectType.SERVICE, clientId), authorization);
    }

    private Long numberClaim(Object value) {
        return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
    }
}
