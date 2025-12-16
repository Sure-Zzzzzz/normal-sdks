package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterRuleMatchCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import redis.embedded.RedisServer;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 规则匹配缓存功能测试
 *
 * @author Sure
 * @since 1.0.2
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = SmartLimiterWebTest.RedisInitializer.class)
public class SmartRedisLimiterRuleMatchCacheTest {

    private static RedisServer redisServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SmartRedisLimiterRuleMatchCache smartRedisLimiterRuleMatchCache;

    @Autowired
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() {
        log.info("=== 测试前准备：清理缓存和Redis ===");

        // 清空规则缓存
        smartRedisLimiterRuleMatchCache.clearCache();

        // 清理Redis数据
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }
    }

    @AfterEach
    public void cleanup() {
        // 清理Redis数据
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }

        // 清空规则缓存
        smartRedisLimiterRuleMatchCache.clearCache();
    }

    /**
     * 测试1：缓存命中
     * 验证同一个路径的多次请求会使用缓存
     */
    @Test
    public void testCacheHit() throws Exception {
        log.info("=== 测试缓存命中功能 ===");

        // 验证初始缓存为空
        assertEquals(0, smartRedisLimiterRuleMatchCache.getCacheSize(), "初始缓存应该为空");

        // 第一次请求 - 缓存未命中，会执行匹配并缓存结果
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        // 验证缓存中有1条记录
        int sizeAfterFirst = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("第一次请求后缓存大小: {}", sizeAfterFirst);
        assertTrue(sizeAfterFirst >= 1, "第一次请求后缓存应该至少有1条记录");

        // 第二次相同请求 - 缓存命中
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        // 缓存大小不变
        int sizeAfterSecond = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("第二次请求后缓存大小: {}", sizeAfterSecond);
        assertEquals(sizeAfterFirst, sizeAfterSecond, "缓存命中后大小应该不变");

        // 第三次相同请求 - 再次缓存命中
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        assertEquals(sizeAfterFirst, smartRedisLimiterRuleMatchCache.getCacheSize(),
                "多次请求同一路径，缓存大小应该保持不变");

        log.info("=== 缓存命中测试通过，缓存大小: {} ===", smartRedisLimiterRuleMatchCache.getCacheSize());
    }

    /**
     * 测试2：不同路径分别缓存
     */
    @Test
    public void testMultiplePathsCache() throws Exception {
        log.info("=== 测试多路径缓存 ===");

        // 请求不同路径
        mockMvc.perform(get("/api/public/test")).andExpect(status().isOk());
        mockMvc.perform(get("/api/user/123")).andExpect(status().isOk());
        mockMvc.perform(get("/api/user/456")).andExpect(status().isOk());
        mockMvc.perform(post("/api/user/789")).andExpect(status().isOk());

        // 验证缓存了多条不同的路径
        // GET:/api/public/test, GET:/api/user/123, GET:/api/user/456, POST:/api/user/789
        int cacheSize = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("缓存了 {} 条路径规则", cacheSize);
        assertTrue(cacheSize >= 4, "应该缓存至少4条不同的路径，实际: " + cacheSize);

        // 再次请求已缓存的路径，缓存大小不应增长
        int sizeBefore = smartRedisLimiterRuleMatchCache.getCacheSize();
        mockMvc.perform(get("/api/public/test")).andExpect(status().isOk());
        mockMvc.perform(get("/api/user/123")).andExpect(status().isOk());
        assertEquals(sizeBefore, smartRedisLimiterRuleMatchCache.getCacheSize(),
                "重复请求已缓存路径，缓存大小不应增长");

        log.info("=== 多路径缓存测试通过，最终缓存大小: {} ===", cacheSize);
    }

    /**
     * 测试3：缓存清空功能
     */
    @Test
    public void testCacheClear() throws Exception {
        log.info("=== 测试缓存清空功能 ===");

        // 先填充缓存
        mockMvc.perform(get("/api/public/test")).andExpect(status().isOk());
        mockMvc.perform(get("/api/user/123")).andExpect(status().isOk());
        mockMvc.perform(post("/api/user/456")).andExpect(status().isOk());

        int sizeBeforeClear = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("清空前缓存大小: {}", sizeBeforeClear);
        assertTrue(sizeBeforeClear > 0, "清空前缓存应该有内容");

        // 清空缓存
        smartRedisLimiterRuleMatchCache.clearCache();

        assertEquals(0, smartRedisLimiterRuleMatchCache.getCacheSize(), "清空后缓存应该为空");
        log.info("缓存已清空");

        // 再次请求会重新填充缓存
        mockMvc.perform(get("/api/public/test")).andExpect(status().isOk());

        int sizeAfterClear = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("清空后再次请求，缓存大小: {}", sizeAfterClear);
        assertTrue(sizeAfterClear > 0, "清空后再次请求应该重新缓存");

        log.info("=== 缓存清空测试通过 ===");
    }

    /**
     * 测试4：HTTP方法区分缓存
     */
    @Test
    public void testHttpMethodCache() throws Exception {
        log.info("=== 测试HTTP方法区分缓存 ===");

        // 同一个路径，不同HTTP方法应该分别缓存
        mockMvc.perform(get("/api/user/123")).andExpect(status().isOk());
        int sizeAfterGet = smartRedisLimiterRuleMatchCache.getCacheSize();

        mockMvc.perform(post("/api/user/123")).andExpect(status().isOk());
        int sizeAfterPost = smartRedisLimiterRuleMatchCache.getCacheSize();

        log.info("GET请求后缓存大小: {}, POST请求后缓存大小: {}", sizeAfterGet, sizeAfterPost);

        // POST后缓存应该比GET后多（因为是不同的缓存Key）
        assertTrue(sizeAfterPost > sizeAfterGet,
                "相同路径不同方法应该分别缓存，POST后应该比GET后多");

        // 再次请求相同的方法，缓存大小不应增长
        mockMvc.perform(get("/api/user/123")).andExpect(status().isOk());
        assertEquals(sizeAfterPost, smartRedisLimiterRuleMatchCache.getCacheSize(),
                "重复请求GET，缓存大小不应增长");

        mockMvc.perform(post("/api/user/123")).andExpect(status().isOk());
        assertEquals(sizeAfterPost, smartRedisLimiterRuleMatchCache.getCacheSize(),
                "重复请求POST，缓存大小不应增长");

        log.info("=== HTTP方法区分缓存测试通过，最终缓存大小: {} ===", smartRedisLimiterRuleMatchCache.getCacheSize());
    }

    /**
     * 测试5：缓存性能验证
     * 大量请求同一路径，验证缓存有效性
     */
    @Test
    public void testCachePerformance() throws Exception {
        log.info("=== 测试缓存性能 ===");

        String testPath = "/api/public/test";

        // 第一次请求，触发规则匹配并缓存
        long startFirst = System.currentTimeMillis();
        mockMvc.perform(get(testPath)).andExpect(status().isOk());
        long firstRequestTime = System.currentTimeMillis() - startFirst;

        int cacheSize = smartRedisLimiterRuleMatchCache.getCacheSize();
        assertTrue(cacheSize > 0, "第一次请求后应该有缓存");

        // 后续4次请求都应该命中缓存（总共5次，不触发限流）
        // 规则是5次/10秒，所以总共5次请求刚好在限制内
        long startBatch = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get(testPath)).andExpect(status().isOk());
        }
        long batchTime = System.currentTimeMillis() - startBatch;

        // 验证缓存大小没有增长（说明都命中了缓存）
        assertEquals(cacheSize, smartRedisLimiterRuleMatchCache.getCacheSize(),
                "4次相同请求后，缓存大小不应增长");

        log.info("第一次请求耗时: {}ms, 后续4次请求总耗时: {}ms, 平均每次: {}ms",
                firstRequestTime, batchTime, batchTime / 4.0);

        log.info("=== 缓存性能测试通过 ===");
    }

    /**
     * 测试6：缓存未匹配路径（MISS_MARKER防止缓存穿透）
     */
    @Test
    public void testMissMarkerCache() throws Exception {
        log.info("=== 测试未匹配路径的缓存（防止缓存穿透） ===");

        // 请求一个存在但未配置特定规则的路径（会使用默认规则）
        // /api/annotated 不匹配任何拦截器规则，会使用默认规则
        mockMvc.perform(get("/api/annotated"))
                .andExpect(status().isOk());

        int sizeAfterFirst = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("第一次请求未匹配规则的路径后，缓存大小: {}", sizeAfterFirst);
        assertTrue(sizeAfterFirst > 0, "未匹配的路径也应该被缓存以避免缓存穿透");

        // 多次请求同一路径，缓存大小不应增长（只请求2次，避免触发注解层5次/1秒的限流）
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/annotated")).andExpect(status().isOk());
        }

        int sizeAfterMultiple = smartRedisLimiterRuleMatchCache.getCacheSize();
        log.info("多次请求后，缓存大小: {}", sizeAfterMultiple);
        assertEquals(sizeAfterFirst, sizeAfterMultiple,
                "多次请求同一未匹配路径，缓存大小不应增长");

        log.info("=== MISS_MARKER缓存测试通过（有效防止缓存穿透） ===");
    }
}
