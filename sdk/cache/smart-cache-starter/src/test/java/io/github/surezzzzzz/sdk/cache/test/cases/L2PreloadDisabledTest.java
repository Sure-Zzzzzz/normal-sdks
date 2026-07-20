package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * L2 预刷新总开关测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = {SmartCacheTestApplication.class, L2PreloadDisabledTest.DisabledPreloadConfig.class},
        properties = {
                "io.github.surezzzzzz.sdk.cache.l2.preload.enabled=false",
                "io.github.surezzzzzz.sdk.cache.l2.expire-seconds=10"
        }
)
class L2PreloadDisabledTest extends BaseSmartCacheTest {

    private static final String CACHE_NAME = "preload-disabled-test";

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @SpyBean
    private L2Cache l2Cache;

    @Autowired
    private DisabledPreloadHandler handler;

    @BeforeEach
    void setUp() {
        requireRedisAvailable();
        cacheManager.clear(CACHE_NAME);
        handler.reset();
        clearInvocations(l2Cache);
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(CACHE_NAME);
        handler.reset();
    }

    @Test
    @DisplayName("关闭预刷新时 needPreload 返回 true 不得调用 handler 或 reload")
    void shouldNotPreloadWhenDisabledEvenIfHandlerReturnsTrue() {
        handler.setNeedPreloadResult(Optional.of(true));
        String key = "handler-true-key";

        assertEquals("old-value", cacheManager.get(CACHE_NAME, key, () -> "old-value"), "首次读取应写入缓存");
        l1Cache.evict(CACHE_NAME, key);
        clearInvocations(l2Cache);

        assertEquals("old-value", cacheManager.get(CACHE_NAME, key, () -> "unexpected-value"), "L2 命中应返回旧值");
        verify(l2Cache, never()).getTtl(CACHE_NAME, key);
        assertEquals(0, handler.getNeedPreloadCount(), "关闭总开关时不得调用 needPreload");
        assertEquals(0, handler.getReloadCount(), "关闭总开关时不得触发 reload");
        log.info("验证通过：handler 的 true 结果不能绕过预刷新总开关");
    }

    @Test
    @DisplayName("关闭预刷新时 needPreload 返回 empty 不得查询 TTL 或 reload")
    void shouldNotFallbackToTtlCheckWhenDisabled() {
        handler.setNeedPreloadResult(Optional.empty());
        String key = "handler-empty-key";

        assertEquals("old-value", cacheManager.get(CACHE_NAME, key, () -> "old-value"), "首次读取应写入缓存");
        l1Cache.evict(CACHE_NAME, key);
        clearInvocations(l2Cache);

        assertEquals("old-value", cacheManager.get(CACHE_NAME, key, () -> "unexpected-value"), "L2 命中应返回旧值");
        verify(l2Cache, never()).getTtl(CACHE_NAME, key);
        assertEquals(0, handler.getNeedPreloadCount(), "关闭总开关时不得调用 needPreload 或回退 TTL 判断");
        assertEquals(0, handler.getReloadCount(), "关闭总开关时不得触发 reload");
        log.info("验证通过：empty 结果不能在关闭状态下触发 TTL 回退或预刷新");
    }

    @Configuration
    static class DisabledPreloadConfig {

        @Bean
        DisabledPreloadHandler disabledPreloadHandler() {
            return new DisabledPreloadHandler();
        }
    }

    static class DisabledPreloadHandler implements CachePreloadHandler {

        private final AtomicInteger needPreloadCount = new AtomicInteger();
        private final AtomicInteger reloadCount = new AtomicInteger();
        private volatile Optional<Boolean> needPreloadResult = Optional.empty();

        @Override
        public boolean support(String cacheName) {
            return CACHE_NAME.equals(cacheName);
        }

        @Override
        public Object reload(String cacheName, String key) {
            reloadCount.incrementAndGet();
            return "refreshed-value";
        }

        @Override
        public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
            needPreloadCount.incrementAndGet();
            return needPreloadResult;
        }

        int getNeedPreloadCount() {
            return needPreloadCount.get();
        }

        int getReloadCount() {
            return reloadCount.get();
        }

        void setNeedPreloadResult(Optional<Boolean> result) {
            needPreloadResult = result;
        }

        void reset() {
            needPreloadCount.set(0);
            reloadCount.set(0);
            needPreloadResult = Optional.empty();
        }
    }
}
