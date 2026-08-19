package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskApplicationAuthorizationService;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AKSK内省授权快照响应处理器。
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class AkskIntrospectionResponseHandler implements AuthenticationSuccessHandler {

    private final AkskApplicationAuthorizationService applicationAuthorizationService;
    private final HttpMessageConverter<OAuth2TokenIntrospection> responseConverter =
            new OAuth2TokenIntrospectionHttpMessageConverter();

    /**
     * 按当前授权投影生成内省响应。
     *
     * @param request 当前请求
     * @param response 当前响应
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
        ApplicationAuthorizationContext authorization = applicationAuthorizationService.loadActiveContext(
                (String) clientId, (Instant) issuedAt, (Instant) expiresAt);
        if (authorization == null) {
            return inactive();
        }
        Map<String, Object> currentClaims = new LinkedHashMap<String, Object>(claims);
        currentClaims.put(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return OAuth2TokenIntrospection.withClaims(currentClaims).build();
    }

    private OAuth2TokenIntrospection inactive() {
        return OAuth2TokenIntrospection.builder().build();
    }
}
