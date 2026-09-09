package io.github.surezzzzzz.sdk.auth.iam.server.token;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ValidationException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationAuthorizationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Token Customizer：将用户角色 + 权限注入到 JWT claims
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamJwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final RoleService roleService;
    private final IamUserRepository userRepository;
    private final SessionService sessionService;
    private final IamApplicationAuthorizationService applicationAuthorizationService;

    /**
     * 按 token 类别注入 claims（sub / sid / auth_time / roles / permissions / 应用授权投影等）
     */
    @Override
    public void customize(JwtEncodingContext context) {
        IamSessionEntity session = currentActiveSession(context);
        if (session == null) {
            return;
        }
        IamUserEntity user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null || !user.getUsername().equals(context.getAuthorization().getPrincipalName())) {
            return;
        }
        Long userId = user.getId();
        context.getClaims().subject(String.valueOf(userId));
        if (session != null) {
            context.getClaims().claim("sid", session.getId());
            if (session.getAuthTime() != null) {
                context.getClaims().claim("auth_time", session.getAuthTime().getEpochSecond());
            }
        }
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            addIdentityClaims(context, user);
            return;
        }
        addApplicationAuthorization(context, userId);
        try {
            List<String> roles = roleService.getUserRoles(userId)
                    .stream()
                    .map(r -> "ROLE_" + r.getCode())
                    .collect(Collectors.toList());
            List<String> permissions = roleService.getUserPermissionCodes(userId);

            context.getClaims().claim(SimpleIamCoreConstant.CLAIM_COMPATIBILITY_ROLES, roles);
            context.getClaims().claim(SimpleIamCoreConstant.CLAIM_COMPATIBILITY_PERMISSIONS, permissions);
        } catch (Exception e) {
            log.warn("兼容身份 claim 注入失败：userId={}, error={}", userId, e.getMessage());
        }
    }

    private void addApplicationAuthorization(JwtEncodingContext context, Long userId) {
        Long applicationId = applicationAuthorizationService.findApplicationIdByOAuthClientId(
                context.getRegisteredClient().getClientId());
        if (applicationId == null) {
            throw new ValidationException("OAuth客户端未归属可信应用");
        }
        Instant issuedAt = context.getClaims().build().getIssuedAt();
        Instant expiresAt = context.getClaims().build().getExpiresAt();
        ApplicationAuthorizationContext authorization = applicationAuthorizationService.loadActiveContext(
                userId, applicationId, issuedAt, expiresAt);
        if (authorization == null) {
            throw new ValidationException("当前用户未获应用资源授权");
        }
        context.getClaims().claim(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
    }

    private void addIdentityClaims(JwtEncodingContext context, IamUserEntity user) {
        if (context.getAuthorizedScopes().contains("profile")) {
            context.getClaims().claim("name", user.getDisplayName());
            context.getClaims().claim("preferred_username", user.getUsername());
        }
        if (context.getAuthorizedScopes().contains("email") && user.getEmail() != null) {
            context.getClaims().claim("email", user.getEmail());
        }
        if (context.getAuthorizedScopes().contains("phone") && user.getPhone() != null) {
            context.getClaims().claim("phone_number", user.getPhone());
        }
    }

    private IamSessionEntity currentActiveSession(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return null;
        }
        String iamSessionId = authorization.getAttribute(
                SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID);
        return sessionService.findActiveById(iamSessionId);
    }
}
