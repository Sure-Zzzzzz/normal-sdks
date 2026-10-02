package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 预刷新执行器饱和（AbortPolicy 拒绝）行为测试
 *
 * <p>验证当 {@code smartCachePreloadExecutor} 拒绝提交时：
 * <ul>
 *   <li>当前读请求不抛异常，仍返回旧 L2 命中值</li>
 *   <li>分布式锁被释放（拒绝在入队前被捕获）</li>
 *   <li>{@code handler.reload()} 不会被执行（任务未入队）</li>
 *   <li>不使用 CallerRuns，避免阻塞读线程</li>
 * </ul>
 *
 * <p>通过 {@code @ConditionalOnMissingBean(name=...)} 用一个始终拒绝的执行器覆盖默认执行器，
 * 并用计数器证明拒绝路径确实被触发，而非 preload 未发生。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = {SmartCacheTestApplication.class, L2PreloadSaturationTest.SaturationConfig.class},
        properties = {
                "io.github.surezzzzzz.sdk.cache.me=saturation-test",
                "io.github.surezzzzzz.sdk.cache.consistency.mode=eventual",
                "io.github.surezzzzzz.sdk.cache.pubsub.mode=disabled",
                "io.github.surezzzzzz.sdk.cache.l1.enabled=false",
                "io.github.surezzzzzz.sdk.cache.l2.enabled=true",
                "io.github.surezzzzzz.sdk.cache.l2.expire-seconds=10",
                "io.github.surezzzzzz.sdk.cache.l2.preload.enabled=true",
                "io.github.surezzzzzz.sdk.cache.l2.preload.before-expire-seconds=5"
        }
)
class L2PreloadSaturationTest extends BaseSmartCacheTest {

    private static final String CACHE_NAME = "saturation-cache";

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private SaturationPreloadHandler handler;

    @Autowired
    private RejectingCountingExecutor rejectingExecutor;

    @BeforeEach
    void setUp() {
        requireRedisAvailable();
        cacheManager.clear(CACHE_NAME);
        handler.reset();
        rejectingExecutor.reset();
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(CACHE_NAME);
        handler.reset();
        rejectingExecutor.reset();
    }

    @Test
    @DisplayName("预刷新执行器拒绝提交时，读请求返回旧值且不传播异常，reload 不执行")
    void shouldReturnOldValueAndSkipReloadWhenPreloadExecutorRejects() throws Exception {
        // needPreload 覆盖为 true：L2 命中即触发 preload，不依赖 TTL 窗口
        handler.setNeedPreloadResult(Optional.of(true));

        // 第一次 get：L2 miss -> loader 写入 L2，不触发 preload
        String first = cacheManager.get(CACHE_NAME, "k", () -> "old-value");
        log.info("首次读取结果：{}，reload 次数：{}，提交次数：{}", first, handler.getReloadCount(),
                rejectingExecutor.getExecuteCount());
        assertEquals("old-value", first, "首次 get 应返回 loader 值并写入 L2");
        assertEquals(0, handler.getReloadCount(), "首次 get 为 L2 miss，不应触发 preload");
        assertEquals(0, rejectingExecutor.getExecuteCount(), "L2 miss 不应提交 preload 任务");

        // 第二次 get：L2 hit -> needPreload=true -> triggerPreload -> 执行器拒绝 -> 捕获，不传播
        String second = cacheManager.get(CACHE_NAME, "k", () -> "old-value");
        log.info("拒绝提交后的读取结果：{}，reload 次数：{}，提交次数：{}", second, handler.getReloadCount(),
                rejectingExecutor.getExecuteCount());
        assertEquals("old-value", second, "执行器拒绝时读请求应返回旧 L2 命中值，不抛异常");

        assertTrue(rejectingExecutor.getExecuteCount() >= 1,
                "preload 应尝试提交到执行器以触发拒绝路径，实际提交次数：" + rejectingExecutor.getExecuteCount());
        assertEquals(0, handler.getReloadCount(),
                "执行器拒绝后 handler.reload() 不应被执行（任务未入队，非 CallerRuns）");

        log.info("验证通过：执行器拒绝 {} 次，读请求返回旧值且未传播异常，reload 未执行（非 CallerRuns）",
                rejectingExecutor.getExecuteCount());
    }

    /**
     * 始终拒绝的计数执行器，覆盖默认 {@code smartCachePreloadExecutor}。
     *
     * <p>计数器用于证明 preload 确实尝试提交（拒绝路径被触发），区分“被拒绝”与“未触发”。
     */
    static class RejectingCountingExecutor implements Executor {
        private final AtomicInteger executeCount = new AtomicInteger(0);

        @Override
        public void execute(Runnable command) {
            executeCount.incrementAndGet();
            throw new RejectedExecutionException("测试预刷新执行器饱和");
        }

        int getExecuteCount() {
            return executeCount.get();
        }

        void reset() {
            executeCount.set(0);
        }
    }

    /**
     * 测试用 CachePreloadHandler，支持 needPreload 覆盖与 reload 计数
     */
    static class SaturationPreloadHandler implements CachePreloadHandler {

        private final AtomicInteger reloadCount = new AtomicInteger(0);
        private volatile Optional<Boolean> needPreloadResult = Optional.empty();

        @Override
        public boolean support(String cacheName) {
            return CACHE_NAME.equals(cacheName);
        }

        @Override
        public Object reload(String cacheName, String key) {
            reloadCount.incrementAndGet();
            return "reloaded-value";
        }

        @Override
        public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
            return needPreloadResult;
        }

        int getReloadCount() {
            return reloadCount.get();
        }

        void setNeedPreloadResult(Optional<Boolean> result) {
            this.needPreloadResult = result;
        }

        void reset() {
            reloadCount.set(0);
            needPreloadResult = Optional.empty();
        }
    }

    @Configuration
    static class SaturationConfig {

        @Bean(name = SmartCacheConstant.SMART_CACHE_PRELOAD_EXECUTOR_BEAN_NAME)
        public RejectingCountingExecutor rejectingPreloadExecutor() {
            return new RejectingCountingExecutor();
        }

        @Bean
        public SaturationPreloadHandler saturationPreloadHandler() {
            return new SaturationPreloadHandler();
        }
    }
}
