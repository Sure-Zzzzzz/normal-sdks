package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskApplicationAuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskIntrospectionResponseHandler;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * AKSK内省响应时间claim截断测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class AkskIntrospectionResponseHandlerTest {

    private static final long ISSUED_AT_SECONDS = 1787819314L;
    private static final long EXPIRES_AT_SECONDS = 1787819374L;

    /**
     * 验证亚秒签发时刻在响应组装前被截断到整秒，iat永不晚于真实签发时刻。
     *
     * @throws Exception 响应写出异常
     */
    @Test
    void shouldTruncateSubSecondTimestampsBeforeAssemblingIntrospectionClaims() throws Exception {
        CapturingAuthorizationService service = new CapturingAuthorizationService();
        AkskIntrospectionResponseHandler handler = new AkskIntrospectionResponseHandler(service);

        MockHttpServletResponse response = writeIntrospection(handler, activeClaims());

        Map<String, Object> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        long issuedAt = ((Number) body.get(OAuth2TokenIntrospectionClaimNames.IAT)).longValue();
        long expiresAt = ((Number) body.get(OAuth2TokenIntrospectionClaimNames.EXP)).longValue();
        long notBefore = ((Number) body.get(OAuth2TokenIntrospectionClaimNames.NBF)).longValue();

        log.info("截断后iat={}, exp={}, nbf={}", issuedAt, expiresAt, notBefore);
        assertEquals(ISSUED_AT_SECONDS, issuedAt, "亚秒iat必须截断，不得四舍五入超前真实签发时刻");
        assertEquals(EXPIRES_AT_SECONDS, expiresAt, "亚秒exp必须统一截断到整秒");
        assertEquals(ISSUED_AT_SECONDS - 1L, notBefore, "nbf仅统一秒表示，iat-1语义必须保留");
        assertEquals(ISSUED_AT_SECONDS, service.capturedIssuedAt.getEpochSecond(),
                "授权快照组装必须收到整秒iat");
        assertEquals(0L, service.capturedIssuedAt.getNano(), "快照iat不得携带亚秒");
        assertEquals(ISSUED_AT_SECONDS, authorizationIssuedAt(body), "快照issued_at必须与顶层iat一致");
    }

    /**
     * 验证非active令牌仍返回inactive响应。
     *
     * @throws Exception 响应写出异常
     */
    @Test
    void shouldReturnInactiveIntrospectionForInactiveToken() throws Exception {
        CapturingAuthorizationService service = new CapturingAuthorizationService();
        AkskIntrospectionResponseHandler handler = new AkskIntrospectionResponseHandler(service);

        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, Boolean.FALSE);
        MockHttpServletResponse response = writeIntrospection(handler, claims);

        Map<String, Object> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        assertFalse(Boolean.TRUE.equals(body.get(OAuth2TokenIntrospectionClaimNames.ACTIVE)), "非active令牌必须返回inactive");
    }

    private MockHttpServletResponse writeIntrospection(AkskIntrospectionResponseHandler handler,
                                                       Map<String, Object> claims) throws Exception {
        OAuth2TokenIntrospection tokenClaims = OAuth2TokenIntrospection.withClaims(claims).build();
        OAuth2TokenIntrospectionAuthenticationToken authentication = new OAuth2TokenIntrospectionAuthenticationToken(
                "token-value", new UsernamePasswordAuthenticationToken("client", "secret"), tokenClaims);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);
        return response;
    }

    private Map<String, Object> activeClaims() {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, Boolean.TRUE);
        claims.put(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID, "client-a");
        claims.put(OAuth2TokenIntrospectionClaimNames.IAT, Instant.ofEpochSecond(ISSUED_AT_SECONDS, 720000000L));
        claims.put(OAuth2TokenIntrospectionClaimNames.EXP, Instant.ofEpochSecond(EXPIRES_AT_SECONDS, 720000000L));
        claims.put(OAuth2TokenIntrospectionClaimNames.NBF, Instant.ofEpochSecond(ISSUED_AT_SECONDS - 1L, 720000000L));
        return claims;
    }

    private long authorizationIssuedAt(Map<String, Object> body) {
        Map<?, ?> authorization = (Map<?, ?>) body.get(SimpleAkskServerConstant.JWT_CLAIM_APPLICATION_AUTHORIZATION);
        return ((Number) authorization.get(SimpleApplicationAuthorizationConstant.FIELD_ISSUED_AT)).longValue();
    }

    /**
     * 捕获快照组装入参的授权服务。
     */
    private static final class CapturingAuthorizationService extends AkskApplicationAuthorizationService {

        private Instant capturedIssuedAt;

        private CapturingAuthorizationService() {
            super(null, null);
        }

        @Override
        public ApplicationAuthorizationContext loadActiveContext(String clientId, Instant issuedAt, Instant expiresAt) {
            this.capturedIssuedAt = issuedAt;
            return new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL,
                    SimpleApplicationAuthorizationConstant.VERSION,
                    ApplicationAuthorizationSubjectType.SERVICE,
                    clientId,
                    "app-a",
                    true,
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.singletonList("resource.read"),
                    null,
                    1L,
                    "manifest-a",
                    "digest-a",
                    issuedAt,
                    expiresAt);
        }
    }
}
