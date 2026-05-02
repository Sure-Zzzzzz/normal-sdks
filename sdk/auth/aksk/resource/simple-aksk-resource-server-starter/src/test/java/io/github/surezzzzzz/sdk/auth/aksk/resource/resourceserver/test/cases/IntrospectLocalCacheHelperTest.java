package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntrospectLocalCacheHelper 单元测试
 *
 * <p>纯单元测试，不启动 Spring 上下文，直接实例化 Helper 验证缓存行为。
 *
 * @author surezzzzzz
 */
@Slf4j
class IntrospectLocalCacheHelperTest {

    private IntrospectLocalCacheHelper disabledHelper;
    private IntrospectLocalCacheHelper enabledHelper;

    @BeforeEach
    void setUp() {
        // 禁用缓存的 helper
        SimpleAkskResourceServerProperties disabledProps = buildProperties(false, 5, 10000);
        disabledHelper = new IntrospectLocalCacheHelper(disabledProps);
        disabledHelper.init();

        // 启用缓存的 helper（TTL=5s，maxSize=10000）
        SimpleAkskResourceServerProperties enabledProps = buildProperties(true, 5, 10000);
        enabledHelper = new IntrospectLocalCacheHelper(enabledProps);
        enabledHelper.init();
    }

    @Test
    @DisplayName("disabled: isEnabled 返回 false")
    void testIsEnabledWhenDisabled() {
        log.info("isEnabled={}", disabledHelper.isEnabled());
        assertFalse(disabledHelper.isEnabled(), "disabled 时 isEnabled 应返回 false");
    }

    @Test
    @DisplayName("enabled: isEnabled 返回 true")
    void testIsEnabledWhenEnabled() {
        log.info("isEnabled={}", enabledHelper.isEnabled());
        assertTrue(enabledHelper.isEnabled(), "enabled 时 isEnabled 应返回 true");
    }

    @Test
    @DisplayName("disabled: get 返回 null 不抛异常")
    void testGetReturnNullWhenDisabled() {
        IntrospectResult result = disabledHelper.get("any-token");
        log.info("get result={}", result);
        assertNull(result, "disabled 时 get 应返回 null");
    }

    @Test
    @DisplayName("cache miss: get 返回 null")
    void testGetReturnNullOnCacheMiss() {
        IntrospectResult result = enabledHelper.get("non-existent-token");
        log.info("cache miss result={}", result);
        assertNull(result, "cache miss 时 get 应返回 null");
    }

    @Test
    @DisplayName("cache hit: get 返回正确的 IntrospectResult")
    void testGetReturnResultOnCacheHit() {
        String token = "test-token-hit";
        IntrospectResult expected = buildResult(true);
        enabledHelper.put(token, expected);

        IntrospectResult actual = enabledHelper.get(token);
        log.info("put result={}, get result={}", expected, actual);

        assertNotNull(actual, "cache hit 时 get 不应返回 null");
        assertEquals(expected, actual, "get 应返回 put 的同一对象");
    }

    @Test
    @DisplayName("disabled: put 不抛异常，get 仍返回 null")
    void testPutNoOpWhenDisabled() {
        assertDoesNotThrow(() -> disabledHelper.put("token", buildResult(true)));
        assertNull(disabledHelper.get("token"), "disabled 时 put 后 get 仍应返回 null");
        log.info("put on disabled cache: no exception, get returns null");
    }

    @Test
    @DisplayName("put 后 get 字段完整一致")
    void testPutAndGetConsistency() {
        String token = "test-token-consistency";
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("client_id", "AKP123");
        attrs.put("scope", "read write");
        IntrospectResult expected = new IntrospectResult(true, attrs);

        enabledHelper.put(token, expected);
        IntrospectResult actual = enabledHelper.get(token);

        log.info("expected={}", expected);
        log.info("actual={}", actual);

        assertNotNull(actual);
        assertTrue(actual.isActive());
        assertEquals("AKP123", actual.getAttributes().get("client_id"));
        assertEquals("read write", actual.getAttributes().get("scope"));
    }

    @Test
    @DisplayName("active=false 的结果也被缓存")
    void testActiveFalseAlsoCached() {
        String token = "revoked-token";
        IntrospectResult revokedResult = buildResult(false);
        enabledHelper.put(token, revokedResult);

        IntrospectResult actual = enabledHelper.get(token);
        log.info("revoked token cached result={}", actual);

        assertNotNull(actual, "active=false 的结果也应被缓存，不应返回 null");
        assertFalse(actual.isActive(), "缓存的 active 应为 false");
    }

    @Test
    @DisplayName("TTL 过期后 get 返回 null")
    void testCacheExpiry() throws InterruptedException {
        // 使用极短 TTL（1s）的 helper
        SimpleAkskResourceServerProperties props = buildProperties(true, 1, 10000);
        IntrospectLocalCacheHelper shortTtlHelper = new IntrospectLocalCacheHelper(props);
        shortTtlHelper.init();

        String token = "expiry-token";
        shortTtlHelper.put(token, buildResult(true));

        IntrospectResult before = shortTtlHelper.get(token);
        log.info("before expiry: result={}", before);
        assertNotNull(before, "TTL 未到期时应命中缓存");

        TimeUnit.SECONDS.sleep(2);

        IntrospectResult after = shortTtlHelper.get(token);
        log.info("after expiry: result={}", after);
        assertNull(after, "TTL 过期后 get 应返回 null");
    }

    @Test
    @DisplayName("超过 maxSize 时不抛异常且触发淘汰")
    void testMaxSizeEviction() {
        SimpleAkskResourceServerProperties props = buildProperties(true, 60, 10);
        IntrospectLocalCacheHelper smallHelper = new IntrospectLocalCacheHelper(props);
        smallHelper.init();

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 20; i++) {
                smallHelper.put("token-" + i, buildResult(true));
            }
        });
        // Caffeine 淘汰是异步的，需要手动触发同步清理后再断言
        smallHelper.cleanUp();
        log.info("maxSize eviction: no exception thrown for 20 entries with maxSize=10");

        // 验证早期插入的条目已被淘汰（maxSize=10，插入了20个，最早的应被驱逐）
        assertNull(smallHelper.get("token-0"), "First entry should have been evicted when maxSize=10 and 20 entries were inserted");
        assertNull(smallHelper.get("token-1"), "Second entry should have been evicted when maxSize=10 and 20 entries were inserted");

        // 验证最新插入的条目仍然存在
        assertNotNull(smallHelper.get("token-19"), "Last entry should still be in cache");
        assertNotNull(smallHelper.get("token-18"), "Second-to-last entry should still be in cache");

        log.info("maxSize eviction verified: early entries evicted, recent entries retained");
    }

    // ==================== 工具方法 ====================

    private SimpleAkskResourceServerProperties buildProperties(boolean enabled, int expireSeconds, int maxSize) {
        SimpleAkskResourceServerProperties props = new SimpleAkskResourceServerProperties();
        SimpleAkskResourceServerProperties.Introspect introspect = new SimpleAkskResourceServerProperties.Introspect();
        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig cacheConfig =
                new SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig();
        cacheConfig.setEnabled(enabled);
        cacheConfig.setExpireSeconds(expireSeconds);
        cacheConfig.setMaxSize(maxSize);
        introspect.setLocalCache(cacheConfig);
        props.setIntrospect(introspect);
        return props;
    }

    private IntrospectResult buildResult(boolean active) {
        return new IntrospectResult(active, Collections.singletonMap("client_id", "AKP123"));
    }
}
