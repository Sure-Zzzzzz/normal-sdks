package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPasswordResetEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis Repository 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class RedisRepositoryTest {

    private final String randomId = UUID.randomUUID().toString();
    private final String sessionId = "session-" + randomId;
    private final String familyId = "family-" + randomId;
    private final String resetToken = "reset-" + randomId;
    private final String username = "test-user-" + randomId;

    @Autowired
    private RedisKeyHelper redisKeyHelper;

    @Autowired
    private RedisTokenRepository redisTokenRepository;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @AfterEach
    void cleanupData() {
        redisTokenRepository.deleteSession(sessionId);
        redisTokenRepository.deleteRefreshFamily(familyId);
        redisTokenRepository.deletePasswordReset(resetToken);
        redisTokenRepository.deleteLoginFailure(username);
    }

    @Test
    @DisplayName("RedisKeyHelper 应按 sure-auth-iam:{businessType}:{me}::{dataId} 规范构造 key")
    void testRedisKeySpec() {
        String sessionKey = redisKeyHelper.buildSessionKey("session-1");
        String refreshFamilyKey = redisKeyHelper.buildRefreshFamilyKey("family-1");
        String loginFailureKey = redisKeyHelper.buildLoginFailureKey("user-1");

        assertEquals("sure-auth-iam:session:{test-app}::session-1", sessionKey);
        assertEquals("sure-auth-iam:refresh-family:{test-app}::family-1", refreshFamilyKey);
        assertEquals("sure-auth-iam:login-flow:{test-app}::user-1", loginFailureKey);
    }

    @Test
    @DisplayName("Session 热数据应以 Route JSON 字符串保存、按 TTL 读取并精确删除")
    void testSessionSaveGetDelete() {
        IamSessionEntity session = session(sessionId);
        Duration ttl = Duration.ofMinutes(5);
        redisTokenRepository.saveSession(sessionId, session, ttl);

        String key = redisKeyHelper.buildSessionKey(sessionId);
        String rawJson = redisRouteTemplate.stringTemplate().opsForValue().get(key);
        Long remainingSeconds = redisRouteTemplate.stringTemplate().getExpire(key);
        IamSessionEntity cached = redisTokenRepository.getSession(sessionId);

        assertNotNull(rawJson, "Route Redis 必须保存 JSON 字符串");
        assertTrue(rawJson.contains("\"id\":\"" + sessionId + "\""), "JSON 必须包含会话标识");
        assertFalse(rawJson.contains("@class"), "JSON 不得写入多态类型元数据");
        assertNotNull(remainingSeconds, "运行态键必须设置 TTL");
        assertTrue(remainingSeconds > 0 && remainingSeconds <= ttl.getSeconds(), "TTL 必须处于指定范围");
        assertNotNull(cached, "JSON 必须可按显式会话类型反序列化");
        assertEquals(sessionId, cached.getId());
        assertEquals(session.getUsername(), cached.getUsername());

        assertEquals(Boolean.TRUE, redisTokenRepository.deleteSession(sessionId));
        assertNull(redisTokenRepository.getSession(sessionId));
        assertNull(redisRouteTemplate.stringTemplate().opsForValue().get(key), "精确删除后不得残留 session 键");
    }

    @Test
    @DisplayName("登录失败计数应以 Route 原子递增并设置 TTL")
    void testLoginFailureCounter() {
        Duration ttl = Duration.ofMinutes(5);
        Long first = redisTokenRepository.incrementLoginFailure(username, ttl);
        Long second = redisTokenRepository.incrementLoginFailure(username, ttl);
        String key = redisKeyHelper.buildLoginFailureKey(username);
        Long remainingSeconds = redisRouteTemplate.stringTemplate().getExpire(key);

        assertEquals(1L, first);
        assertEquals(2L, second);
        assertEquals("2", redisRouteTemplate.stringTemplate().opsForValue().get(key));
        assertEquals(2, redisTokenRepository.getLoginFailureCount(username));
        assertNotNull(remainingSeconds, "计数键必须设置 TTL");
        assertTrue(remainingSeconds > 0 && remainingSeconds <= ttl.getSeconds(), "计数 TTL 必须处于指定范围");

        assertEquals(Boolean.TRUE, redisTokenRepository.deleteLoginFailure(username));
        assertEquals(0, redisTokenRepository.getLoginFailureCount(username));
    }

    @Test
    @DisplayName("Refresh Token 族和密码重置凭证应按各自确定类型写入 JSON")
    void testTypedRuntimeStateRoundTrip() {
        IamRefreshTokenFamilyEntity family = refreshFamily(familyId);
        IamPasswordResetEntity reset = passwordReset(resetToken);
        Duration ttl = Duration.ofMinutes(5);

        redisTokenRepository.saveRefreshFamily(familyId, family, ttl);
        redisTokenRepository.savePasswordReset(resetToken, reset, ttl);

        IamRefreshTokenFamilyEntity cachedFamily = redisTokenRepository.getRefreshFamily(familyId);
        IamPasswordResetEntity cachedReset = redisTokenRepository.getPasswordReset(resetToken);

        assertNotNull(cachedFamily, "Refresh Token 族必须按确定类型读取");
        assertEquals(familyId, cachedFamily.getId());
        assertEquals(family.getCurrentTokenHash(), cachedFamily.getCurrentTokenHash());
        assertNotNull(cachedReset, "密码重置凭证必须按确定类型读取");
        assertEquals(reset.getId(), cachedReset.getId());
        assertEquals(reset.getTokenHash(), cachedReset.getTokenHash());

        assertEquals(Boolean.TRUE, redisTokenRepository.deleteRefreshFamily(familyId));
        assertEquals(Boolean.TRUE, redisTokenRepository.deletePasswordReset(resetToken));
        assertNull(redisTokenRepository.getRefreshFamily(familyId));
        assertNull(redisTokenRepository.getPasswordReset(resetToken));
    }

    private IamSessionEntity session(String id) {
        IamSessionEntity entity = new IamSessionEntity();
        entity.setId(id);
        entity.setUserId(1L);
        entity.setUsername(username);
        entity.setIssuer("http://localhost:8180");
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        return entity;
    }

    private IamRefreshTokenFamilyEntity refreshFamily(String id) {
        IamRefreshTokenFamilyEntity entity = new IamRefreshTokenFamilyEntity();
        entity.setId(id);
        entity.setUserId(1L);
        entity.setUsername(username);
        entity.setCurrentTokenHash("current-hash");
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        return entity;
    }

    private IamPasswordResetEntity passwordReset(String token) {
        IamPasswordResetEntity entity = new IamPasswordResetEntity();
        entity.setId("credential-" + randomId);
        entity.setUserId(1L);
        entity.setUsername(username);
        entity.setTokenHash("hash-" + token);
        entity.setRequestedBy("admin");
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        return entity;
    }
}
