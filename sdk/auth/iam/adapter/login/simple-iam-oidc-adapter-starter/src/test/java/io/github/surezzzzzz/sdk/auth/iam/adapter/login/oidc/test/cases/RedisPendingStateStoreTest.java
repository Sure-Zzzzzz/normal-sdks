package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.constant.SimpleIamOidcAdapterConstant;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.exception.SimpleIamOidcAdapterException;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.model.PendingAuthorization;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.RedisPendingStateStore;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OIDC 授权暂存上下文 Redis 存储单测（mock RedisRouteTemplate，锁定 key 形态、
 * TTL 与 getAndDelete 原子取删契约）。
 *
 * <p>真实 Redis 下的跨实例语义由 IamOidcLoginEndToEndTest（宿主带 server-starter，
 * 装配自动走 Redis 分支）覆盖。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisPendingStateStoreTest {

    private static final String STATE = "unit-state-1";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private RedisRouteTemplate redisRouteTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    private RedisPendingStateStore store;

    @BeforeEach
    void setUp() {
        when(redisRouteTemplate.stringTemplateByKey(anyString())).thenReturn(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisPendingStateStore(redisRouteTemplate);
    }

    @Test
    @DisplayName("save 应以规范 key 写入 JSON 并带 TTL")
    void saveWritesJsonWithTtl() throws Exception {
        store.save(STATE, new PendingAuthorization("nonce-1", "http://localhost:18080/cb"),
                Duration.ofMinutes(10));

        String expectedKey = SimpleIamOidcAdapterConstant.PENDING_KEY_PREFIX + STATE;
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(expectedKey),
                jsonCaptor.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
        JsonNode json = objectMapper.readTree(jsonCaptor.getValue());
        log.info("save 写入 key={}，json={}", expectedKey, jsonCaptor.getValue());
        assertEquals("nonce-1", json.get("nonce").asText());
        assertEquals("http://localhost:18080/cb", json.get("callbackUrl").asText());
        verify(redisRouteTemplate).stringTemplateByKey(expectedKey);
    }

    @Test
    @DisplayName("consume 应 getAndDelete 原子取回并还原上下文")
    void consumeGetsAndDeletesAtomically() {
        when(valueOperations.getAndDelete(anyString())).thenReturn(
                "{\"nonce\":\"nonce-1\",\"callbackUrl\":\"http://localhost:18080/cb\"}");

        PendingAuthorization pending = store.consume(STATE);

        log.info("consume 原子取回：nonce={}, callbackUrl={}",
                pending.getNonce(), pending.getCallbackUrl());
        assertEquals("nonce-1", pending.getNonce());
        assertEquals("http://localhost:18080/cb", pending.getCallbackUrl());
        String expectedKey = SimpleIamOidcAdapterConstant.PENDING_KEY_PREFIX + STATE;
        verify(valueOperations).getAndDelete(expectedKey);
    }

    @Test
    @DisplayName("consume 对不存在的 state 应返回 null")
    void consumeMissingStateReturnsNull() {
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        assertNull(store.consume(STATE));
        log.info("不存在的 state consume 返回 null");
    }

    @Test
    @DisplayName("存储内容反序列化失败应抛适配器异常且带错误码")
    void consumeCorruptedJsonThrowsAdapterException() {
        when(valueOperations.getAndDelete(anyString())).thenReturn("not-a-json{{{");

        SimpleIamOidcAdapterException exception = assertThrows(SimpleIamOidcAdapterException.class,
                () -> store.consume(STATE));
        log.info("坏 JSON 消费：errorCode={}", exception.getErrorCode());
        assertEquals(SimpleIamOidcAdapterConstant.ERROR_PENDING_STATE_IO, exception.getErrorCode());
    }

    @Test
    @DisplayName("TTL 常量应为 10 分钟（用户在 IdP 页面停留的往返预算）")
    void ttlConstantIsTenMinutes() {
        assertEquals(10, SimpleIamOidcAdapterConstant.PENDING_TTL_MINUTES);
        assertTrue(SimpleIamOidcAdapterConstant.PENDING_KEY_PREFIX.startsWith("sure-auth-iam:"));
        log.info("常量锁定：TTL={} 分钟，key 前缀={}",
                SimpleIamOidcAdapterConstant.PENDING_TTL_MINUTES,
                SimpleIamOidcAdapterConstant.PENDING_KEY_PREFIX);
    }
}
