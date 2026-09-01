package io.github.surezzzzzz.sdk.auth.aksk.server.resourceserver;

import com.nimbusds.jwt.JWTClaimsSet;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
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

/**
 * AKSK Server自签JWE资源认证适配器。
 * <p>
 * server验自签token无需内省（自身即权威源），本地解密等价于旧oauth2ResourceServer(jwt)链；
 * 主体一致性校验与应用授权claim提取对齐resource侧{@code AkskResourceAuthenticationAdapter}模式。
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
        return authenticated(claims);
    }

    private ResourceAuthenticationResult authenticated(JWTClaimsSet claims) {
        Object clientIdClaim = claims.getClaim(JwtClaimConstant.CLIENT_ID);
        if (!(clientIdClaim instanceof String)) {
            log.debug("AKSK Server资源认证拒绝：clientId claim缺失或非字符串");
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.SUBJECT_INVALID);
        }
        ApplicationAuthorizationContext authorization;
        try {
            authorization = ApplicationAuthorizationContextClaimMapper.fromClaim(
                    claims.getClaim(JwtClaimConstant.APPLICATION_AUTHORIZATION));
        } catch (RuntimeException exception) {
            log.debug("AKSK Server资源认证拒绝：应用授权claim解析失败，异常={}", exception.getMessage());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        String clientId = (String) clientIdClaim;
        if (authorization.getSubjectType() != ApplicationAuthorizationSubjectType.SERVICE
                || !clientId.equals(authorization.getSubjectId())) {
            log.debug("AKSK Server资源认证拒绝：主体不一致，clientId={}, subjectType={}",
                    clientId, authorization.getSubjectType());
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        log.debug("AKSK Server资源认证通过：clientId={}", clientId);
        return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(
                SOURCE_ID, ResourceSubjectType.SERVICE, clientId), authorization);
    }
}
