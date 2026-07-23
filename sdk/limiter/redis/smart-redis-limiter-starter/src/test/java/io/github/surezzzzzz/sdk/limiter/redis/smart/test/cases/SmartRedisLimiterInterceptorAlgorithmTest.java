package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.support.TestEventListener;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 拦截器模式 + 算法选择 端到端测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>拦截器 + 滑动窗口算法基本限流</li>
 *   <li>拦截器 + 滑动窗口算法精度（窗口边界）</li>
 *   <li>拦截器 + 滑动窗口算法并发限流</li>
 *   <li>拦截器 + 混用算法（滑动窗口GET + 固定窗口POST）</li>
 *   <li>拦截器 + 滑动窗口算法Redis Key结构验证</li>
 * </ul>
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
@AutoConfigureMockMvc
public class SmartRedisLimiterInterceptorAlgorithmTest {

    private static final String TEST_SERVICE_NAME = "test-service";
    private static final long MATRIX_SHORT_WINDOW_SECONDS = 30L;
    private static final long MATRIX_LONG_WINDOW_SECONDS = 60L;
    private static final String[][] REDIS_MATRIX = createRedisMatrix();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private TestEventListener eventListener;

    private static String[][] createRedisMatrix() {
        String[][] coreMatrix = {
                {"redis3-standalone", "redis3Standalone", SmartRedisLimiterConstant.REDIS_MODE_STANDALONE},
                {"redis5-standalone", "redis5Standalone", SmartRedisLimiterConstant.REDIS_MODE_STANDALONE},
                {"redis7-standalone", "redis7Standalone", SmartRedisLimiterConstant.REDIS_MODE_STANDALONE},
                {"redis3-cluster", "redis3Cluster", SmartRedisLimiterConstant.REDIS_MODE_CLUSTER},
                {"redis5-cluster", "redis5Cluster", SmartRedisLimiterConstant.REDIS_MODE_CLUSTER}
        };
        if (System.getProperty("spring.profiles.active", "").contains("2.2.x")) {
            return coreMatrix;
        }
        return new String[][]{
                coreMatrix[0], coreMatrix[1], coreMatrix[2], coreMatrix[3], coreMatrix[4],
                {"redis7-cluster", "redis7Cluster", SmartRedisLimiterConstant.REDIS_MODE_CLUSTER}
        };
    }

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== 测试前准备：清理Redis ===");
        eventListener.reset();
        cleanRedis();
        // 预热Redis连接
        try {
            mockMvc.perform(get("/api/health"));
        } catch (Exception e) {
            log.warn("Redis预热失败: {}", e.getMessage());
        }
    }

    @AfterEach
    public void cleanup() {
        cleanRedis();
        eventListener.reset();
    }

    // ==================== 滑动窗口算法测试 ====================

    private void cleanRedis() {
        try {
            Set<String> keys = redisRouteTemplate.stringTemplate().keys(
                    SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisRouteTemplate.stringTemplate().delete(keys);
                log.info("清理了 {} 个限流key", keys.size());
            }
        } catch (Exception e) {
            log.warn("清理Redis失败: {}", e.getMessage());
        }
    }

    /**
     * 测试1：拦截器 + 滑动窗口基本限流（5次/10秒）
     */
    @Test
    public void testInterceptorSlidingWindowBasic() throws Exception {
        log.info("=== 测试拦截器 + 滑动窗口基本限流（5次/10秒） ===");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/" + i))
                    .andExpect(status().isOk());
        }

        // 第6次应该被限流
        mockMvc.perform(get("/api/sliding/over-limit"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        log.info("=== 拦截器滑动窗口基本限流测试通过 ===");
    }

    /**
     * 测试2：拦截器 + 滑动窗口精度测试（窗口边界）
     */
    @Test
    public void testInterceptorSlidingWindowPrecision() throws Exception {
        log.info("=== 测试拦截器滑动窗口精度（窗口边界） ===");

        // 快速请求5次，耗尽配额
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/fast-" + i))
                    .andExpect(status().isOk());
        }

        // 第6次应该被限流，验证窗口确实已满
        mockMvc.perform(get("/api/sliding/fast-exceed"))
                .andExpect(status().isTooManyRequests());

        // 等待窗口过期
        Thread.sleep(1100);

        // 窗口重置，应该又能请求
        mockMvc.perform(get("/api/sliding/after-window"))
                .andExpect(status().isOk());

        log.info("=== 拦截器滑动窗口精度测试通过 ===");
    }

    // ==================== 混用算法测试 ====================

    /**
     * 测试3：拦截器 + 滑动窗口并发限流（20并发，5次限制）
     */
    @Test
    public void testInterceptorSlidingWindowConcurrent() throws Exception {
        log.info("=== 测试拦截器滑动窗口并发限流（20并发，5次限制） ===");

        int limit = 5;
        int concurrentRequests = 20;
        int poolSize = Math.min(concurrentRequests, 50);

        ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            int taskId = i;
            executorService.submit(() -> {
                try {
                    mockMvc.perform(get("/api/sliding/concurrent-" + taskId))
                            .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (SmartRedisLimitExceededException e) {
                    log.debug("任务 {} 被限流(exception)", taskId);
                    failCount.incrementAndGet();
                } catch (AssertionError e) {
                    // MockMvc 返回 429 时 andExpect(status().isOk()) 抛 AssertionError
                    log.debug("任务 {} 被限流(429)", taskId);
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("任务 {} 发生异常: {}", taskId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有任务应该在10秒内完成");
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "线程池应该在5秒内关闭");

        log.info("成功次数: {}, 失败次数: {}", successCount.get(), failCount.get());
        // 滑动窗口并发结果允许有±1的误差
        assertTrue(successCount.get() <= limit + 1, "成功次数不应超过限流阈值过多");
        assertTrue(failCount.get() >= concurrentRequests - limit - 1, "失败次数应该接近超出限流的请求数");

        log.info("=== 拦截器滑动窗口并发限流测试通过 ===");
    }

    // ==================== Redis Key结构测试 ====================

    /**
     * 测试6：拦截器模式混用算法（滑动窗口GET + 固定窗口POST）
     */
    @Test
    public void testInterceptorMixedAlgorithm() throws Exception {
        log.info("=== 测试拦截器模式混用算法 ===");

        // GET请求用滑动窗口（5次/10秒）
        log.info("测试滑动窗口GET（5次/10秒）...");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/mixed/get-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/mixed/get-over"))
                .andExpect(status().isTooManyRequests());

        // 等待滑动窗口过期
        Thread.sleep(1100);

        // 滑动窗口重置，GET应恢复
        mockMvc.perform(get("/api/mixed/get-recover"))
                .andExpect(status().isOk());

        // POST请求用固定窗口（3次/10秒）
        log.info("测试固定窗口POST（3次/10秒）...");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/mixed/post-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/mixed/post-over"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        // 等待固定窗口重置（窗口对齐到秒边界）
        Thread.sleep(1100);

        // 固定窗口重置，POST应恢复
        mockMvc.perform(post("/api/mixed/post-recover"))
                .andExpect(status().isOk());

        log.info("=== 混用算法测试通过 ===");
    }

    /**
     * 测试7：拦截器 + 滑动窗口Redis Key结构验证
     */
    @Test
    public void testInterceptorSlidingWindowKeyStructure() throws Exception {
        log.info("=== 测试拦截器滑动窗口Redis Key结构 ===");

        // 触发滑动窗口限流
        mockMvc.perform(get("/api/sliding/key-test"))
                .andExpect(status().isOk());

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("滑动窗口限流后的keys: {}", keys);

        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "应该有限流key");

        // 滑动窗口key应该包含 "sw" 后缀
        boolean hasSlidingWindowKey = keys.stream()
                .anyMatch(key -> key.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
        assertTrue(hasSlidingWindowKey,
                "滑动窗口的key应该包含 [" + SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW + "] 后缀，实际keys: " + keys);

        log.info("=== 拦截器滑动窗口Key结构测试通过 ===");
    }

    // ==================== ClientIP 获取测试 ====================

    /**
     * 测试8：拦截器模式固定窗口 vs 滑动窗口Key结构对比
     */
    @Test
    public void testInterceptorFixedVsSlidingKeyStructure() throws Exception {
        log.info("=== 测试拦截器固定窗口 vs 滑动窗口Key结构对比 ===");

        // 触发固定窗口限流（使用/api/public路径）
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        // 触发滑动窗口限流
        mockMvc.perform(get("/api/sliding/sliding-key-test"))
                .andExpect(status().isOk());

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("所有keys: {}", keys);

        assertNotNull(keys);

        // 2.0 固定窗口使用 fw2 used counter namespace
        boolean hasFixedWindowKey = keys.stream()
                .anyMatch(k -> k.contains(SmartRedisLimiterStarterConstant.SUFFIX_FIXED_WINDOW_USED_V2)
                        && k.endsWith(SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS));
        assertTrue(hasFixedWindowKey, "应该有固定窗口fw2 used counter key，实际keys: " + keys);

        // 滑动窗口key包含sw后缀
        boolean hasSlidingWindowKey = keys.stream()
                .anyMatch(k -> k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
        assertTrue(hasSlidingWindowKey, "应该有滑动窗口的key（包含sw后缀），实际keys: " + keys);

        log.info("=== 固定窗口 vs 滑动窗口Key结构对比测试通过 ===");
    }

    /**
     * 测试10：ClientIP获取 - X-Forwarded-For优先
     */
    @Test
    public void testClientIpFromXForwardedFor() throws Exception {
        log.info("=== 测试ClientIP获取 - X-Forwarded-For优先 ===");

        String expectedIp = "203.0.113.50";

        // 触发限流以发布事件（/api/sliding限流规则：5次/10秒）
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/ip-xff-" + i)
                            .header("X-Forwarded-For", expectedIp))
                    .andExpect(status().isOk());
        }
        // 第6次触发限流，发布事件
        mockMvc.perform(get("/api/sliding/ip-xff-6")
                        .header("X-Forwarded-For", expectedIp))
                .andExpect(status().isTooManyRequests());

        // 验证事件中的clientIp
        boolean received = eventListener.limitEventLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Listener 应收到限流事件");

        SmartRedisLimiterRecord record = eventListener.records.stream()
                .filter(r -> SmartRedisLimiterConstant.SOURCE_INTERCEPTOR.equals(r.getSource()))
                .findFirst()
                .orElse(null);
        assertNotNull(record, "应收到拦截器来源的事件");
        assertEquals(expectedIp, record.getClientIp(),
                "ClientIP应从X-Forwarded-For获取，实际: " + record.getClientIp());

        log.info("=== X-Forwarded-For优先测试通过，事件IP={} ===", record.getClientIp());
    }

    /**
     * 测试11：ClientIP获取 - 多级代理取第一个IP
     */
    @Test
    public void testClientIpMultiProxyFirstIp() throws Exception {
        log.info("=== 测试ClientIP获取 - 多级代理取第一个IP ===");

        String expectedIp = "203.0.113.50";

        // 模拟多级代理，X-Forwarded-For包含多个IP
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/ip-multi-" + i)
                            .header("X-Forwarded-For", "203.0.113.50, 198.51.100.178, 192.0.2.1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/sliding/ip-multi-6")
                        .header("X-Forwarded-For", "203.0.113.50, 198.51.100.178, 192.0.2.1"))
                .andExpect(status().isTooManyRequests());

        boolean received = eventListener.limitEventLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Listener 应收到限流事件");

        SmartRedisLimiterRecord record = eventListener.records.stream()
                .filter(r -> SmartRedisLimiterConstant.SOURCE_INTERCEPTOR.equals(r.getSource()))
                .findFirst()
                .orElse(null);
        assertNotNull(record, "应收到拦截器来源的事件");
        assertEquals(expectedIp, record.getClientIp(),
                "多级代理时应取第一个IP，实际: " + record.getClientIp());

        log.info("=== 多级代理取第一个IP测试通过，事件IP={} ===", record.getClientIp());
    }

    /**
     * 测试12：ClientIP获取 - X-Forwarded-For为空时使用X-Real-IP
     */
    @Test
    public void testClientIpFallbackToXRealIp() throws Exception {
        log.info("=== 测试ClientIP获取 - X-Forwarded-For为空时使用X-Real-IP ===");

        String expectedIp = "198.51.100.100";

        // X-Forwarded-For为空，fallback到X-Real-IP
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/ip-real-" + i)
                            .header("X-Forwarded-For", "")
                            .header("X-Real-IP", expectedIp))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/sliding/ip-real-6")
                        .header("X-Forwarded-For", "")
                        .header("X-Real-IP", expectedIp))
                .andExpect(status().isTooManyRequests());

        boolean received = eventListener.limitEventLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Listener 应收到限流事件");

        SmartRedisLimiterRecord record = eventListener.records.stream()
                .filter(r -> SmartRedisLimiterConstant.SOURCE_INTERCEPTOR.equals(r.getSource()))
                .findFirst()
                .orElse(null);
        assertNotNull(record, "应收到拦截器来源的事件");
        assertEquals(expectedIp, record.getClientIp(),
                "X-Forwarded-For为空时应从X-Real-IP获取，实际: " + record.getClientIp());

        log.info("=== X-Real-IP fallback测试通过，事件IP={} ===", record.getClientIp());
    }

    // ==================== 响应头测试 ====================

    /**
     * 测试13：ClientIP获取 - 无代理头时使用RemoteAddr
     */
    @Test
    public void testClientIpFallbackToRemoteAddr() throws Exception {
        log.info("=== 测试ClientIP获取 - 无代理头时使用RemoteAddr ===");

        // 没有任何代理头，应该fallback到getRemoteAddr()（MockMvc下为 0:0:0:0:0:0:0:1 或 127.0.0.1）
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/ip-raw-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/sliding/ip-raw-6"))
                .andExpect(status().isTooManyRequests());

        boolean received = eventListener.limitEventLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Listener 应收到限流事件");

        SmartRedisLimiterRecord record = eventListener.records.stream()
                .filter(r -> SmartRedisLimiterConstant.SOURCE_INTERCEPTOR.equals(r.getSource()))
                .findFirst()
                .orElse(null);
        assertNotNull(record, "应收到拦截器来源的事件");
        assertNotNull(record.getClientIp(), "ClientIP不应为空");
        assertFalse(record.getClientIp().isEmpty(), "ClientIP不应为空字符串");
        // MockMvc下RemoteAddr为 IPv6本地 或 127.0.0.1
        log.info("无代理头时事件ClientIP={}", record.getClientIp());

        log.info("=== RemoteAddr fallback测试通过 ===");
    }

    /**
     * 测试14：拦截器限流时响应包含 X-RateLimit-* 头（通过请求）
     */
    @Test
    public void testRateLimitHeadersOnPass() throws Exception {
        log.info("=== 测试限流通过时的响应头 ===");

        // 第一个请求，通过
        MvcResult result = mockMvc.perform(get("/api/sliding/header-pass"))
                .andExpect(status().isOk())
                .andReturn();

        String limitHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT);
        String remainingHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING);
        String resetHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_RESET);

        log.info("通过请求响应头: limit={}, remaining={}, reset={}", limitHeader, remainingHeader, resetHeader);
        assertNotNull(limitHeader, "X-RateLimit-Limit 不应为空");
        assertEquals("5", limitHeader, "limit 应为 5");
        assertNotNull(remainingHeader, "X-RateLimit-Remaining 不应为空");
        assertNotNull(resetHeader, "X-RateLimit-Reset 不应为空");

        log.info("=== 限流通过响应头测试通过 ===");
    }

    /**
     * 测试15：拦截器限流时响应包含 X-RateLimit-* 头（拒绝请求）
     */
    @Test
    public void testRateLimitHeadersOnReject() throws Exception {
        log.info("=== 测试限流拒绝时的响应头 ===");

        // 耗尽配额
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/header-reject-" + i))
                    .andExpect(status().isOk());
        }

        // 第6次被限流，检查响应头
        MvcResult result = mockMvc.perform(get("/api/sliding/header-reject-6"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String limitHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT);
        String remainingHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING);
        String resetHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_RESET);
        String retryAfterHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_RETRY_AFTER);

        log.info("拒绝请求响应头: limit={}, remaining={}, reset={}, retryAfter={}",
                limitHeader, remainingHeader, resetHeader, retryAfterHeader);

        assertEquals("5", limitHeader, "limit 应为 5");
        assertEquals("0", remainingHeader, "remaining 应为 0");
        assertNotNull(resetHeader, "reset 不应为空");
        assertNotNull(retryAfterHeader, "Retry-After 不应为空");

        log.info("=== 限流拒绝响应头测试通过 ===");
    }

    /**
     * 测试16：固定窗口限流响应头
     */
    @Test
    public void testFixedWindowRateLimitHeaders() throws Exception {
        log.info("=== 测试固定窗口限流响应头 ===");

        // 第一个请求，通过
        MvcResult result = mockMvc.perform(get("/api/public/fixed-header"))
                .andExpect(status().isOk())
                .andReturn();

        String limitHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT);
        String remainingHeader = result.getResponse().getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING);

        log.info("固定窗口通过请求响应头: limit={}, remaining={}", limitHeader, remainingHeader);
        assertEquals("5", limitHeader, "固定窗口 limit 应为 5");

        log.info("=== 固定窗口限流响应头测试通过 ===");
    }

    /**
     * 测试17：Redis 版本矩阵固定窗口多窗口限流
     */
    @Test
    public void testRedisVersionMatrixFixedMultiWindow() throws Exception {
        log.info("=== 测试 Redis 版本矩阵固定窗口多窗口限流 ===");

        for (String[] matrixItem : REDIS_MATRIX) {
            String routePath = matrixItem[0];
            String datasourceKey = matrixItem[1];
            String redisMode = matrixItem[2];
            String requestPath = "/api/matrix/" + routePath + "/" + UUID.randomUUID();
            eventListener.reset(1);

            mockMvc.perform(get(requestPath)).andExpect(status().isOk());
            mockMvc.perform(get(requestPath)).andExpect(status().isOk());
            mockMvc.perform(get(requestPath)).andExpect(status().isTooManyRequests());

            assertTrue(eventListener.limitEventLatch.await(3, TimeUnit.SECONDS),
                    datasourceKey + " 固定窗口矩阵测试应收到限流事件");
            SmartRedisLimiterRecord record = lastRecord();
            log.info("固定窗口矩阵结果: datasourceKey={}, redisMode={}, routeKey={}",
                    record.getDatasourceKey(), record.getRedisMode(), record.getRouteKey());
            assertMatrixRouteRecord(record, datasourceKey, redisMode,
                    SmartRedisLimiterConstant.ALGORITHM_FIXED, requestPath);
            assertMatrixKeysOnlyExistOnTarget(record.getRouteKey(), datasourceKey,
                    SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS);
        }

        log.info("=== Redis 版本矩阵固定窗口多窗口限流测试通过 ===");
    }

    /**
     * 测试18：Redis 版本矩阵滑动窗口多窗口限流
     */
    @Test
    public void testRedisVersionMatrixSlidingMultiWindow() throws Exception {
        log.info("=== 测试 Redis 版本矩阵滑动窗口多窗口限流 ===");

        for (String[] matrixItem : REDIS_MATRIX) {
            String routePath = matrixItem[0];
            String datasourceKey = matrixItem[1];
            String redisMode = matrixItem[2];
            String requestPath = "/api/matrix/" + routePath + "/" + UUID.randomUUID();
            eventListener.reset(1);

            mockMvc.perform(post(requestPath)).andExpect(status().isOk());
            mockMvc.perform(post(requestPath)).andExpect(status().isOk());
            mockMvc.perform(post(requestPath)).andExpect(status().isTooManyRequests());

            assertTrue(eventListener.limitEventLatch.await(3, TimeUnit.SECONDS),
                    datasourceKey + " 滑动窗口矩阵测试应收到限流事件");
            SmartRedisLimiterRecord record = lastRecord();
            log.info("滑动窗口矩阵结果: datasourceKey={}, redisMode={}, routeKey={}",
                    record.getDatasourceKey(), record.getRedisMode(), record.getRouteKey());
            assertMatrixRouteRecord(record, datasourceKey, redisMode,
                    SmartRedisLimiterConstant.ALGORITHM_SLIDING, requestPath);
            assertMatrixKeysOnlyExistOnTarget(record.getRouteKey(), datasourceKey,
                    SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW);
        }

        log.info("=== Redis 版本矩阵滑动窗口多窗口限流测试通过 ===");
    }

    private SmartRedisLimiterRecord lastRecord() {
        assertFalse(eventListener.records.isEmpty(), "应至少收到一个限流事件");
        return eventListener.records.get(eventListener.records.size() - 1);
    }

    private void assertMatrixRouteRecord(SmartRedisLimiterRecord record,
                                         String datasourceKey,
                                         String redisMode,
                                         String algorithm,
                                         String requestPath) {
        String expectedRouteKey = SmartRedisLimiterKeyHelper.buildBaseKey(TEST_SERVICE_NAME,
                String.format(SmartRedisLimiterConstant.TEMPLATE_KEY_PATH_WITH_METHOD,
                        SmartRedisLimiterKeyStrategy.PATH.getCode(), requestPath, "GET"));
        if (SmartRedisLimiterConstant.ALGORITHM_SLIDING.equals(algorithm)) {
            expectedRouteKey = SmartRedisLimiterKeyHelper.buildBaseKey(TEST_SERVICE_NAME,
                    String.format(SmartRedisLimiterConstant.TEMPLATE_KEY_PATH_WITH_METHOD,
                            SmartRedisLimiterKeyStrategy.PATH.getCode(), requestPath, "POST"));
        }

        assertNotNull(record, datasourceKey + " 限流事件记录不应为空");
        assertFalse(record.isPassed(), datasourceKey + " 第3次请求应被限流");
        assertEquals(expectedRouteKey, record.getRouteKey(), datasourceKey + " routeKey 应由请求路径精确生成");
        assertEquals(datasourceKey, record.getDatasourceKey(), datasourceKey + " 路由数据源应匹配");
        assertEquals(redisMode, record.getRedisMode(), datasourceKey + " Redis 模式应匹配");
        assertEquals(algorithm, record.getAlgorithm(), datasourceKey + " 限流算法应匹配");
        assertTrue(record.isRouteRequired(), datasourceKey + " 应标记 routeRequired=true");
        assertTrue(record.isRouteResolved(), datasourceKey + " 应标记 routeResolved=true");
    }

    private void assertMatrixKeysOnlyExistOnTarget(String routeKey,
                                                   String datasourceKey,
                                                   String suffix) {
        boolean fixedWindow = SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS.equals(suffix);
        String shortWindowKey = fixedWindow
                ? SmartRedisLimiterKeyHelper.buildFixedUsedWindowKey(
                routeKey, MATRIX_SHORT_WINDOW_SECONDS, true)
                : SmartRedisLimiterKeyHelper.buildWindowKey(
                routeKey, MATRIX_SHORT_WINDOW_SECONDS, suffix, true);
        String longWindowKey = fixedWindow
                ? SmartRedisLimiterKeyHelper.buildFixedUsedWindowKey(
                routeKey, MATRIX_LONG_WINDOW_SECONDS, true)
                : SmartRedisLimiterKeyHelper.buildWindowKey(
                routeKey, MATRIX_LONG_WINDOW_SECONDS, suffix, true);

        log.info("验证矩阵 Redis 物理落点: datasourceKey={}, shortKey={}, longKey={}",
                datasourceKey, shortWindowKey, longWindowKey);
        assertTrue(Boolean.TRUE.equals(redisRouteTemplate.stringTemplate(datasourceKey).hasKey(shortWindowKey)),
                datasourceKey + " 应存在短窗口 key");
        assertTrue(Boolean.TRUE.equals(redisRouteTemplate.stringTemplate(datasourceKey).hasKey(longWindowKey)),
                datasourceKey + " 应存在长窗口 key");

        for (String[] matrixItem : REDIS_MATRIX) {
            String otherDatasourceKey = matrixItem[1];
            if (datasourceKey.equals(otherDatasourceKey)) {
                continue;
            }
            assertFalse(Boolean.TRUE.equals(
                            redisRouteTemplate.stringTemplate(otherDatasourceKey).hasKey(shortWindowKey)),
                    datasourceKey + " 的短窗口 key 不应落入 " + otherDatasourceKey);
            assertFalse(Boolean.TRUE.equals(
                            redisRouteTemplate.stringTemplate(otherDatasourceKey).hasKey(longWindowKey)),
                    datasourceKey + " 的长窗口 key 不应落入 " + otherDatasourceKey);
        }
    }
}
