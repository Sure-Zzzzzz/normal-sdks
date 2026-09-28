package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskEffectiveAuthorizationResult;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskEffectiveAuthorizationService;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.http.converter.OAuth2TokenIntrospectionHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AKSK内省授权快照响应处理器。
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class AkskIntrospectionResponseHandler implements AuthenticationSuccessHandler {

    private final AkskEffectiveAuthorizationService effectiveAuthorizationService;
    private final HttpMessageConverter<OAuth2TokenIntrospection> responseConverter =
            new OAuth2TokenIntrospectionHttpMessageConverter();

    /**
     * 按当前授权投影生成内省响应。
     *
     * @param request        当前请求
     * @param response       当前响应
     * @param authentication 已完成内省认证
     * @throws IOException 响应写入失败
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2TokenIntrospectionAuthenticationToken introspectionAuthentication =
                (OAuth2TokenIntrospectionAuthenticationToken) authentication;
        OAuth2TokenIntrospection responseBody = rebuildAuthorization(
                introspectionAuthentication.getTokenClaims());
        responseConverter.write(responseBody, null, new ServletServerHttpResponse(response));
    }

    private OAuth2TokenIntrospection rebuildAuthorization(OAuth2TokenIntrospection tokenClaims) {
        Map<String, Object> claims = tokenClaims.getClaims();
        if (!Boolean.TRUE.equals(claims.get(OAuth2TokenIntrospectionClaimNames.ACTIVE))) {
            return inactive();
        }
        Object clientId = claims.get(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID);
        Object issuedAt = claims.get(OAuth2TokenIntrospectionClaimNames.IAT);
        Object expiresAt = claims.get(OAuth2TokenIntrospectionClaimNames.EXP);
        if (!(clientId instanceof String) || !(issuedAt instanceof Instant) || !(expiresAt instanceof Instant)) {
            return inactive();
        }
        // 存储中的签发时刻携带亚秒，经四舍五入可能得到超前整秒（实测 314.72s → iat=315）；
        // 组装前截断到整秒，保证 iat 永不晚于真实签发时刻
        Instant issuedAtSeconds = ((Instant) issuedAt).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAtSeconds = ((Instant) expiresAt).truncatedTo(ChronoUnit.SECONDS);
        AkskEffectiveAuthorizationResult result = effectiveAuthorizationService.resolve(
                (String) clientId, issuedAtSeconds, expiresAtSeconds);
        if (result == null || !matchesIssuedAuthorization(claims, result)) {
            return inactive();
        }
        ApplicationAuthorizationContext authorization = result.getAuthorization();
        Map<String, Object> currentClaims = new LinkedHashMap<String, Object>(claims);
        currentClaims.put(OAuth2TokenIntrospectionClaimNames.IAT, issuedAtSeconds);
        currentClaims.put(OAuth2TokenIntrospectionClaimNames.EXP, expiresAtSeconds);
        truncateNotBeforeClaim(currentClaims);
        currentClaims.put(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return OAuth2TokenIntrospection.withClaims(currentClaims).build();
    }

    /**
     * inherited AKU 必须同时匹配签发时模式、不可变目标和两套当前纪元，避免旧 token 被重新授权复活。
     */
    private boolean matchesIssuedAuthorization(Map<String, Object> claims, AkskEffectiveAuthorizationResult result) {
        Object mode = claims.get(SimpleAkskServerConstant.JWT_CLAIM_AUTHORIZATION_MODE);
        if (!matchesMode(mode, result)) {
            return false;
        }
        if (!AkskOwnerAuthorizationMode.OWNER_INHERITED.equals(result.getAuthorizationMode())) {
            return true;
        }
        return sameLong(claims.get(SimpleAkskServerConstant.JWT_CLAIM_TARGET_APPLICATION_ID),
                result.getTargetApplicationId())
                && sameLong(claims.get(SimpleAkskServerConstant.JWT_CLAIM_OWNER_SECURITY_EPOCH),
                result.getOwnerSecurityEpoch())
                && sameLong(claims.get(SimpleAkskServerConstant.JWT_CLAIM_APPLICATION_AUTHORIZATION_EPOCH),
                result.getApplicationAuthorizationEpoch())
                && sameLong(claims.get(SimpleAkskServerConstant.JWT_CLAIM_OWNER_INHERITED_ACCESS_EPOCH),
                result.getOwnerInheritedAccessEpoch())
                && sameLong(claims.get(SimpleAkskServerConstant.JWT_CLAIM_PROJECTION_ACCESS_EPOCH),
                result.getProjectionAccessEpoch())
                && result.getOwnerSourceId().equals(claims.get(SimpleAkskServerConstant.JWT_CLAIM_OWNER_SOURCE_ID));
    }

    /**
     * 静态历史 token 未携带 mode 时仍按 STATIC_LEGACY 处理；继承 token 则必须显式携带。
     */
    private boolean matchesMode(Object mode, AkskEffectiveAuthorizationResult result) {
        if (AkskOwnerAuthorizationMode.OWNER_INHERITED.equals(result.getAuthorizationMode())) {
            return AkskAuthorizationMode.OWNER_INHERITED.name().equals(mode);
        }
        return mode == null || AkskAuthorizationMode.STATIC_LEGACY.name().equals(mode);
    }

    private boolean sameLong(Object actual, Long expected) {
        return actual instanceof Number && expected != null
                && ((Number) actual).longValue() == expected.longValue();
    }

    /**
     * 统一 nbf 的秒表示：仅截断亚秒，不改变其取值逻辑。
     *
     * @param currentClaims 当前响应 claims
     */
    private void truncateNotBeforeClaim(Map<String, Object> currentClaims) {
        Object notBefore = currentClaims.get(OAuth2TokenIntrospectionClaimNames.NBF);
        if (notBefore instanceof Instant) {
            currentClaims.put(OAuth2TokenIntrospectionClaimNames.NBF,
                    ((Instant) notBefore).truncatedTo(ChronoUnit.SECONDS));
        }
    }

    private OAuth2TokenIntrospection inactive() {
        return OAuth2TokenIntrospection.builder().build();
    }
}
