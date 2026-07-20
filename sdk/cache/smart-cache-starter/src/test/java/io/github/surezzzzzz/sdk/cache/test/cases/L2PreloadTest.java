package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 异步续期（Preload）测试
 *
 * <p>验证 L2 preload 功能：
 * <ul>
 *   <li>preload 启用时，L2 TTL 剩余 &lt; before-expire-seconds 时异步触发 handler.reload()</li>
 *   <li>preload 触发时返回旧值，不阻塞当前请求</li>
 *   <li>handler.reload() 失败时旧值仍可返回（容错窗口）</li>
 *   <li>覆盖 needPreload() 可完全替代框架的 TTL 查询</li>
 *   <li>无 handler 注册时静默跳过</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = {SmartCacheTestApplication.class, L2PreloadTest.TestPreloadConfig.class},
        properties = {
                "io.github.surezzzzzz.sdk.cache.l1.expire-seconds=3",
                "io.github.surezzzzzz.sdk.cache.l1.refresh-seconds=10",
                "io.github.surezzzzzz.sdk.cache.l2.preload.enabled=true",
                "io.github.surezzzzzz.sdk.cache.l2.preload.before-expire-seconds=5",
                "io.github.surezzzzzz.sdk.cache.l2.expire-seconds=10"
        }
)
class L2PreloadTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L2Cache l2Cache;

    @Autowired
    private TestCachePreloadHandler testHandler;

    private static final String CACHE_NAME = "preload-test";

    @BeforeEach
    void setUp() {
        requireRedisAvailable();
        cacheManager.clear(CACHE_NAME);
        testHandler.reset();
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(CACHE_NAME);
        testHandler.reset();
    }

    @Test
    @DisplayName("框架查 TTL 触发 preload，返回旧值，续期后返回新值")
    void testPreloadViaFrameworkTtlCheck() throws Exception {
        log.info("========== 测试：框架查 TTL 触发 preload ==========");

        String key = "ttl-trigger-key";
        testHandler.setNextValue("new-value");

        // 第一次 get，写入 L2（TTL=10s）
        String result1 = cacheManager.get(CACHE_NAME, key, () -> "old-value");
        assertEquals("old-value", result1);
        assertEquals(0, testHandler.getReloadCount(), "初始时 handler 不应被调用");

        // 等待 L1 过期（3s）且 L2 TTL 剩余 < before-expire-seconds（5s），即等待约 6s
        log.info("等待 L1 过期且 L2 TTL 进入预刷新窗口（约 6s）...");
        Thread.sleep(6000);

        // L2 TTL 剩余约 4s < 5s，框架查 TTL 后触发 preload，返回旧值
        String result2 = cacheManager.get(CACHE_NAME, key, () -> "old-value");
        assertEquals("old-value", result2, "preload 触发时应返回旧值，不阻塞当前请求");

        // 等待异步续期完成
        Thread.sleep(1000);
        assertEquals(1, testHandler.getReloadCount(),
                "框架查 TTL 后应触发 handler.reload() 恰好一次");

        // 续期后应返回新值
        String result3 = cacheManager.get(CACHE_NAME, key, () -> "old-value");
        assertEquals("new-value", result3, "续期后应返回新值");

        log.info("✓ 框架查 TTL 触发 preload，旧值正常返回，续期后新值写入");
    }

    @Test
    @DisplayName("覆盖 needPreload() 返回 true，不查 TTL 直接触发")
    void testPreloadViaNeedPreloadOverride() throws Exception {
        log.info("========== 测试：覆盖 needPreload() 直接触发 ==========");

        String key = "need-preload-override-key";
        testHandler.setNextValue("refreshed-value");
        testHandler.setNeedPreloadResult(Optional.of(true)); // 覆盖：直接触发，不查 TTL

        // 写入 L2，等待 L1 过期后再触发（L1 TTL=3s）
        cacheManager.get(CACHE_NAME, key, () -> "original-value");

        // 等待 L1 过期
        Thread.sleep(4000);

        // L1 miss → L2 hit，needPreload 返回 true，直接触发
        String result = cacheManager.get(CACHE_NAME, key, () -> "original-value");
        assertEquals("original-value", result, "应返回旧值");

        Thread.sleep(500);
        assertEquals(1, testHandler.getReloadCount(),
                "覆盖 needPreload() 返回 true 时应触发 handler.reload() 恰好一次");

        log.info("✓ 覆盖 needPreload() 直接触发，无需查 TTL");
    }

    @Test
    @DisplayName("覆盖 needPreload() 返回 false，不触发 preload")
    void testPreloadSuppressedByNeedPreload() throws Exception {
        log.info("========== 测试：覆盖 needPreload() 返回 false，不触发 ==========");

        String key = "need-preload-false-key";
        testHandler.setNeedPreloadResult(Optional.of(false)); // 覆盖：不触发

        cacheManager.get(CACHE_NAME, key, () -> "value");

        // 等待进入 TTL 窗口
        Thread.sleep(6000);

        cacheManager.get(CACHE_NAME, key, () -> "value");
        Thread.sleep(500);

        assertEquals(0, testHandler.getReloadCount(),
                "needPreload() 返回 false 时不应触发 handler.reload()");

        log.info("✓ needPreload() 返回 false 时正确跳过");
    }

    @Test
    @DisplayName("handler.reload() 失败时旧值仍可返回（容错窗口）")
    void testPreloadHandlerFailureReturnsOldValue() throws Exception {
        log.info("========== 测试：handler.reload() 失败时旧值仍可返回 ==========");

        String key = "preload-failure-key";
        testHandler.setThrowOnReload(true);

        cacheManager.get(CACHE_NAME, key, () -> "old-value-for-failure-test");

        Thread.sleep(6000);

        String result = cacheManager.get(CACHE_NAME, key, () -> "old-value-for-failure-test");
        assertEquals("old-value-for-failure-test", result, "handler 失败时旧值仍应返回");

        // 关闭 throwOnReload，让飞行中的重试能快速成功结束，避免污染后续测试
        testHandler.setThrowOnReload(false);

        log.info("✓ handler 失败时旧值正常返回（容错窗口生效）");
    }

    @Test
    @DisplayName("getReloadTtlSeconds >0 时续期后 L2 TTL 应接近指定值")
    void testPreloadWithCustomReloadTtl() throws Exception {
        log.info("========== 测试：getReloadTtlSeconds >0 时续期 TTL ==========");

        String key = "reload-ttl-key";
        int reloadTtl = 60;
        testHandler.setNextValue("reloaded-value");
        testHandler.setReloadTtlSeconds(reloadTtl);

        // 写入，等待进入 preload 窗口
        cacheManager.get(CACHE_NAME, key, () -> "original-value");
        Thread.sleep(6000);

        // 触发 preload
        cacheManager.get(CACHE_NAME, key, () -> "original-value");

        // 等待异步续期完成（带重试）
        for (int i = 0; i < 10; i++) {
            if (testHandler.getReloadCount() >= 1) break;
            Thread.sleep(500);
        }

        assertEquals(1, testHandler.getReloadCount(), "应触发一次 reload");

        long actualTtl = l2Cache.getTtl(CACHE_NAME, key);
        // reloadTtl=60, offset=60*0.1=6, 范围 [54, 66]，但 L2 expire-seconds=10，
        // 所以续期用 60s 写入
        log.info("getReloadTtlSeconds={} 续期后 L2 TTL={}", reloadTtl, actualTtl);
        assertTrue(actualTtl > 0, "TTL 应大于 0");
        // 范围 [54, 66]，给点余量
        assertTrue(actualTtl >= 50 && actualTtl <= 70,
                "TTL 应接近 " + reloadTtl + "s，实际: " + actualTtl);

        testHandler.setReloadTtlSeconds(0); // reset
        log.info("✓ getReloadTtlSeconds >0 时续期后 TTL 正确");
    }

    @Test
    @DisplayName("getReloadTtlSeconds=0 时续期后 L2 TTL 应与全局配置一致")
    void testPreloadWithDefaultReloadTtl() throws Exception {
        log.info("========== 测试：getReloadTtlSeconds=0 时续期 TTL ==========");

        String key = "reload-default-ttl-key";
        // getReloadTtlSeconds 默认 0，使用全局配置（10s）
        testHandler.setNextValue("reloaded-value-2");

        cacheManager.get(CACHE_NAME, key, () -> "original-value-2");
        Thread.sleep(6000);

        cacheManager.get(CACHE_NAME, key, () -> "original-value-2");

        // 等待异步续期完成（带重试）
        for (int i = 0; i < 10; i++) {
            if (testHandler.getReloadCount() >= 1) break;
            Thread.sleep(500);
        }

        assertEquals(1, testHandler.getReloadCount(), "应触发一次 reload");

        // 等待异步写入完成
        Thread.sleep(500);

        long actualTtl = l2Cache.getTtl(CACHE_NAME, key);
        // 全局 expire-seconds=10, offset=10*0.1=1, 范围 [9, 11]，给点余量
        log.info("getReloadTtlSeconds=0 续期后 L2 TTL={}", actualTtl);
        assertTrue(actualTtl > 0, "TTL 应大于 0");
        assertTrue(actualTtl >= 7 && actualTtl <= 12,
                "TTL 应接近全局配置 10s，实际: " + actualTtl);

        log.info("✓ getReloadTtlSeconds=0 时续期后 TTL 与全局配置一致");
    }

    /**
     * 测试用 CachePreloadHandler 配置
     */
    @Configuration
    static class TestPreloadConfig {
        @Bean
        public TestCachePreloadHandler testCachePreloadHandler() {
            return new TestCachePreloadHandler();
        }
    }

    /**
     * 测试用 CachePreloadHandler 实现，支持控制 needPreload 返回值
     */
    static class TestCachePreloadHandler implements CachePreloadHandler {

        private final AtomicInteger reloadCount = new AtomicInteger(0);
        private final AtomicReference<String> nextValue = new AtomicReference<>("new-value");
        private volatile boolean throwOnReload = false;
        private volatile Optional<Boolean> needPreloadResult = Optional.empty();
        private volatile int reloadTtlSeconds = 0;

        @Override
        public boolean support(String cacheName) {
            return CACHE_NAME.equals(cacheName);
        }

        @Override
        public Object reload(String cacheName, String key) {
            reloadCount.incrementAndGet();
            if (throwOnReload) {
                throw new RuntimeException("Simulated reload failure");
            }
            return nextValue.get();
        }

        @Override
        public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
            return needPreloadResult;
        }

        @Override
        public int getReloadTtlSeconds(String cacheName, String key) {
            return reloadTtlSeconds;
        }

        public int getReloadCount() {
            return reloadCount.get();
        }

        public void setNextValue(String value) {
            nextValue.set(value);
        }

        public void setThrowOnReload(boolean throwOnReload) {
            this.throwOnReload = throwOnReload;
        }

        public void setNeedPreloadResult(Optional<Boolean> result) {
            this.needPreloadResult = result;
        }

        public void setReloadTtlSeconds(int ttl) {
            this.reloadTtlSeconds = ttl;
        }

        public void reset() {
            reloadCount.set(0);
            nextValue.set("new-value");
            throwOnReload = false;
            needPreloadResult = Optional.empty();
            reloadTtlSeconds = 0;
        }
    }
}
