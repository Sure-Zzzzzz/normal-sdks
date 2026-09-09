package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamAuthorizeContextStatus;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamAuthorizeContextEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamAuthorizeContextRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamAuthorizeContextService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamAuthorizeContextServiceTest {

    private static final String SERVLET_SESSION_ID = "servlet-session-for-context-test";
    private static final String IAM_SESSION_ID = "iam-session-for-context-test";
    private static final Long USER_ID = 101L;

    @Autowired
    private IamAuthorizeContextService authorizeContextService;

    @Autowired
    private IamAuthorizeContextRepository authorizeContextRepository;

    @Autowired
    private RedisTokenRepository redisTokenRepository;

    @Autowired
    private RedisKeyHelper redisKeyHelper;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @AfterEach
    void cleanup() {
        authorizeContextRepository.findByServletSessionIdHashAndStatusIn(
                        TokenHashHelper.sha256Hex(SERVLET_SESSION_ID),
                        Arrays.asList(IamAuthorizeContextStatus.values()))
                .forEach(context -> {
                    redisTokenRepository.deleteAuthorizeContext(context.getId());
                    authorizeContextRepository.delete(context);
                });
    }

    @Test
    @DisplayName("授权交易应只持久化敏感请求值的哈希并写入带 TTL 的 Redis 热数据")
    void testCreatePendingLoginStoresHashesOnly() {
        IamAuthorizeContextEntity context = createPendingLogin();
        String rawJson = redisRouteTemplate.stringTemplate().opsForValue()
                .get(redisKeyHelper.buildAuthorizeContextKey(context.getId()));
        IamAuthorizeContextEntity stored = authorizeContextRepository.findById(context.getId()).orElse(null);

        assertNotNull(stored);
        assertEquals(IamAuthorizeContextStatus.PENDING_LOGIN, stored.getStatus());
        assertEquals("openid profile", stored.getRequestedScopes());
        assertEquals(TokenHashHelper.sha256Hex("state-value"), stored.getStateHash());
        assertEquals(TokenHashHelper.sha256Hex("nonce-value"), stored.getNonceHash());
        assertEquals(TokenHashHelper.sha256Hex("challenge-value"), stored.getCodeChallengeHash());
        assertEquals(TokenHashHelper.sha256Hex(SERVLET_SESSION_ID), stored.getServletSessionIdHash());
        assertNotNull(rawJson);
        assertFalse(rawJson.contains("state-value"));
        assertFalse(rawJson.contains("nonce-value"));
        assertFalse(rawJson.contains("challenge-value"));
        assertFalse(rawJson.contains(SERVLET_SESSION_ID));
        assertTrue(redisRouteTemplate.stringTemplate()
                .getExpire(redisKeyHelper.buildAuthorizeContextKey(context.getId())) > 0);
    }

    @Test
    @DisplayName("授权交易必须绑定同一 Servlet 会话、用户和 IAM 会话后才能批准与完成")
    void testAuthorizedContextRequiresBoundIdentityAndCompletesOnce() {
        IamAuthorizeContextEntity context = createPendingLogin();
        IamAuthorizeContextEntity pendingConsent = authorizeContextService.bindAuthenticatedSession(
                context.getId(), USER_ID, IAM_SESSION_ID, SERVLET_SESSION_ID, true);

        assertEquals(IamAuthorizeContextStatus.PENDING_CONSENT, pendingConsent.getStatus());
        SimpleIamServerException forbidden = assertThrows(SimpleIamServerException.class,
                () -> authorizeContextService.approve(context.getId(), USER_ID, IAM_SESSION_ID,
                        "another-servlet-session"));
        assertEquals(ErrorCode.AUTHORIZE_CONTEXT_FORBIDDEN, forbidden.getErrorCode());

        IamAuthorizeContextEntity approved = authorizeContextService.approve(
                context.getId(), USER_ID, IAM_SESSION_ID, SERVLET_SESSION_ID);
        IamAuthorizeContextEntity completed = authorizeContextService.complete(
                context.getId(), USER_ID, IAM_SESSION_ID, SERVLET_SESSION_ID);

        assertEquals(IamAuthorizeContextStatus.APPROVED, approved.getStatus());
        assertEquals(IamAuthorizeContextStatus.COMPLETED, completed.getStatus());
        assertNull(redisTokenRepository.getAuthorizeContext(context.getId()));
        SimpleIamServerException replay = assertThrows(SimpleIamServerException.class,
                () -> authorizeContextService.complete(context.getId(), USER_ID, IAM_SESSION_ID,
                        SERVLET_SESSION_ID));
        assertEquals(ErrorCode.AUTHORIZE_CONTEXT_INVALID, replay.getErrorCode());
    }

    @Test
    @DisplayName("过期授权交易必须失效并删除精确 Redis 热数据")
    void testExpiredContextFailsClosed() {
        IamAuthorizeContextEntity context = createPendingLogin();
        context.setExpiresAt(Instant.now().minusSeconds(1));
        authorizeContextRepository.save(context);
        redisTokenRepository.saveAuthorizeContext(context.getId(), context,
                java.time.Duration.ofMinutes(1));

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> authorizeContextService.getActive(context.getId()));

        assertEquals(ErrorCode.AUTHORIZE_CONTEXT_EXPIRED, exception.getErrorCode());
        assertNull(redisTokenRepository.getAuthorizeContext(context.getId()));
        assertEquals(IamAuthorizeContextStatus.EXPIRED,
                authorizeContextRepository.findById(context.getId()).get().getStatus());
    }

    private IamAuthorizeContextEntity createPendingLogin() {
        return authorizeContextService.createPendingLogin(
                "registered-client-id", "client-id", "https://example.com/callback",
                Arrays.asList("profile", "openid", "profile"), "state-value", "nonce-value",
                "challenge-value", "S256", SERVLET_SESSION_ID);
    }
}
