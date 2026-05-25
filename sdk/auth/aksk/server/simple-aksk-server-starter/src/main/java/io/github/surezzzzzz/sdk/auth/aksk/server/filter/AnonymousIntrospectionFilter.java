package io.github.surezzzzzz.sdk.auth.aksk.server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimNames;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anonymous Introspection Filter
 *
 * <p>当 introspect.require-authentication=false 时，在 OAuth2ClientAuthenticationFilter 之前
 * 拦截 /oauth2/introspect 请求，直接查询 token 状态并返回，绕过 client 认证。
 *
 * <p><b>安全警告</b>：仅适用于网络隔离的内网/测试环境。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class AnonymousIntrospectionFilter extends OncePerRequestFilter {

    private static final String INTROSPECT_ENDPOINT = "/oauth2/introspect";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OAuth2AuthorizationService authorizationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!HttpMethod.POST.name().equals(request.getMethod())
                || !INTROSPECT_ENDPOINT.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(SimpleAkskServerConstant.HTTP_BASIC_AUTH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getParameter(SimpleAkskServerConstant.OAUTH2_PARAM_TOKEN);
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);

        if (authorization == null) {
            result.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, false);
        } else {
            org.springframework.security.oauth2.server.authorization.OAuth2Authorization.Token<?> accessToken =
                    authorization.getToken(org.springframework.security.oauth2.core.OAuth2AccessToken.class);
            boolean active = accessToken != null
                    && !accessToken.isInvalidated()
                    && (accessToken.getToken().getExpiresAt() == null
                    || accessToken.getToken().getExpiresAt().isAfter(Instant.now()));

            result.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, active);
            if (active) {
                result.put(OAuth2TokenIntrospectionClaimNames.SUB, authorization.getPrincipalName());
                result.put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, authorization.getPrincipalName());
                result.put(OAuth2TokenIntrospectionClaimNames.SCOPE,
                        String.join(SimpleAkskServerConstant.SCOPE_SEPARATOR_SPACE, authorization.getAuthorizedScopes()));
                if (accessToken.getToken().getIssuedAt() != null) {
                    result.put(OAuth2TokenClaimNames.IAT,
                            accessToken.getToken().getIssuedAt().getEpochSecond());
                }
                if (accessToken.getToken().getExpiresAt() != null) {
                    result.put(OAuth2TokenClaimNames.EXP,
                            accessToken.getToken().getExpiresAt().getEpochSecond());
                }
                Map<String, Object> claims = accessToken.getClaims();
                if (claims != null) {
                    addClaimIfPresent(result, claims, SimpleAkskServerConstant.JWT_CLAIM_CLIENT_TYPE);
                    addClaimIfPresent(result, claims, SimpleAkskServerConstant.JWT_CLAIM_USER_ID);
                    addClaimIfPresent(result, claims, SimpleAkskServerConstant.JWT_CLAIM_USERNAME);
                    addClaimIfPresent(result, claims, SimpleAkskServerConstant.JWT_CLAIM_AUTH_SERVER_ID);
                }
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), result);
        log.debug("Anonymous introspect: token={}..., active={}",
                token.substring(0, Math.min(20, token.length())), result.get(OAuth2TokenIntrospectionClaimNames.ACTIVE));
    }

    private void addClaimIfPresent(Map<String, Object> result, Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value != null) {
            result.put(key, value);
        }
    }
}
