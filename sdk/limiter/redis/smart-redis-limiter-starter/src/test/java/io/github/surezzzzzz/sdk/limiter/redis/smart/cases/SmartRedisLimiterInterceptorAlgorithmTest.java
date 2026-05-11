package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.TestEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
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
 *   <li>拦截器 + 滑动窗口算法 + Redis异常降级</li>
 *   <li>拦截器 + 混用算法（滑动窗口GET + 固定窗口POST）</li>
 *   <li>拦截器 + 滑动窗口算法Redis Key结构验证</li>
 * </ul>
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
public class SmartRedisLimiterInterceptorAlgorithmTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @Autowired
    private TestEventListener eventListener;

    private RedisConnectionFactory originalFactory;

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
        // 恢复原始Redis连接
        if (originalFactory != null) {
            smartRedisLimiterRedisTemplate.setConnectionFactory(originalFactory);
            originalFactory = null;
        }
        cleanRedis();
        eventListener.reset();
    }

    private void cleanRedis() {
        try {
            Set<String> keys = smartRedisLimiterRedisTemplate.keys(
                    SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                smartRedisLimiterRedisTemplate.delete(keys);
                log.info("清理了 {} 个限流key", keys.size());
            }
        } catch (Exception e) {
            log.warn("清理Redis失败: {}", e.getMessage());
        }
    }

    // ==================== 滑动窗口算法测试 ====================

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

    /**
     * 测试4：拦截器 + 滑动窗口 + Redis异常时fallback=allow放行
     */
    @Test
    public void testInterceptorSlidingWindowFallbackAllow() throws Exception {
        log.info("=== 测试拦截器滑动窗口 + Redis异常fallback=allow放行 ===");

        // 验证Redis正常时限流生效
        log.info("验证Redis正常时限流生效...");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/normal-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/sliding/normal-5"))
                .andExpect(status().isTooManyRequests());

        // 停止Redis
        stopRedisAndCloseConnection();

        // fallback=allow，Redis异常时放行
        log.info("Redis异常时，fallback=allow应放行...");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/sliding/fallback-" + i))
                    .andExpect(status().isOk());
        }

        log.info("=== 拦截器滑动窗口fallback=allow测试通过 ===");
    }

    /**
     * 测试5：拦截器 + 滑动窗口 + Redis异常时fallback=deny拒绝
     */
    @Test
    public void testInterceptorSlidingWindowFallbackDeny() throws Exception {
        log.info("=== 测试拦截器滑动窗口 + Redis异常fallback=deny拒绝 ===");

        // 停止Redis
        stopRedisAndCloseConnection();

        // fallback=deny，Redis异常时应拒绝
        log.info("Redis异常时，fallback=deny应拒绝...");
        mockMvc.perform(post("/api/sliding/test"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        log.info("=== 拦截器滑动窗口fallback=deny测试通过 ===");
    }

    // ==================== 混用算法测试 ====================

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

    // ==================== Redis Key结构测试 ====================

    /**
     * 测试7：拦截器 + 滑动窗口Redis Key结构验证
     */
    @Test
    public void testInterceptorSlidingWindowKeyStructure() throws Exception {
        log.info("=== 测试拦截器滑动窗口Redis Key结构 ===");

        // 触发滑动窗口限流
        mockMvc.perform(get("/api/sliding/key-test"))
                .andExpect(status().isOk());

        Set<String> keys = smartRedisLimiterRedisTemplate.keys(
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

        Set<String> keys = smartRedisLimiterRedisTemplate.keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("所有keys: {}", keys);

        assertNotNull(keys);

        // 固定窗口key包含秒后缀但不包含滑动窗口后缀
        boolean hasFixedWindowKey = keys.stream()
                .filter(k -> k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS))
                .anyMatch(k -> !k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
        assertTrue(hasFixedWindowKey, "应该有固定窗口的key（包含s后缀但不包含sw后缀），实际keys: " + keys);

        // 滑动窗口key包含sw后缀
        boolean hasSlidingWindowKey = keys.stream()
                .anyMatch(k -> k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
        assertTrue(hasSlidingWindowKey, "应该有滑动窗口的key（包含sw后缀），实际keys: " + keys);

        log.info("=== 固定窗口 vs 滑动窗口Key结构对比测试通过 ===");
    }

    // ==================== Redis恢复测试 ====================

    /**
     * 测试9：拦截器滑动窗口 + Redis恢复后限流恢复正常
     */
    @Test
    public void testInterceptorSlidingWindowRedisRecovery() throws Exception {
        log.info("=== 测试拦截器滑动窗口Redis恢复 ===");

        // 停止Redis
        stopRedisAndCloseConnection();

        // 降级期间请求成功（fallback=allow）
        mockMvc.perform(get("/api/sliding/during-down"))
                .andExpect(status().isOk());

        // 重启Redis
        startRedisAndInitConnection();

        // 清理数据
        cleanRedis();

        // 限流恢复正常
        log.info("验证限流恢复...");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/sliding/recovered-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/sliding/recovered-over"))
                .andExpect(status().isTooManyRequests());

        log.info("=== 拦截器滑动窗口Redis恢复测试通过 ===");
    }

    // ==================== ClientIP 获取测试 ====================

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

    // ==================== 响应头测试 ====================

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

    // ==================== 辅助方法 ====================

    private void stopRedisAndCloseConnection() throws Exception {
        log.info("模拟Redis不可用：切换到不存在的端口 16379...");
        originalFactory = smartRedisLimiterRedisTemplate.getConnectionFactory();
        LettuceConnectionFactory brokenFactory = new LettuceConnectionFactory("localhost", 16379);
        brokenFactory.afterPropertiesSet();
        smartRedisLimiterRedisTemplate.setConnectionFactory(brokenFactory);
        Thread.sleep(200);
    }

    private void startRedisAndInitConnection() throws Exception {
        log.info("恢复Redis连接...");
        if (originalFactory != null) {
            smartRedisLimiterRedisTemplate.setConnectionFactory(originalFactory);
            originalFactory = null;
        }
        Thread.sleep(200);
    }
}
