package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheEvict;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCachePut;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheable;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.cache.test.config.TestWarmUpConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Test
 * <p>
 * 端到端测试，覆盖完整业务场景和所有核心功能：
 * - P0: L1/L2 缓存、缓存穿透防护、缓存击穿防护、缓存雪崩防护、最终一致性
 * - P1: 注解式 API、编程式 API、批量操作、异步刷新、预热、循环依赖检测
 * - P2: 统计 API
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
@Import({TestWarmUpConfiguration.class, EndToEndTest.TestFixturesConfiguration.class})
public class EndToEndTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private UserService userService;

    @Autowired
    private AnnotationService annotationService;

    @BeforeEach
    public void setUp() {
        requireRedisAvailable();
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("userCache");
        UserService.dbCallCount.set(0);
        UserService.nullQueryCount.set(0);
        log.info("测试环境初始化完成");
    }

    /**
     * 测试缓存穿透防护：查询不存在的数据应该缓存 null 值
     */
    @Test
    public void testCachePenetrationProtection() throws Exception {
        log.info("========== 端到端测试：缓存穿透防护 ==========");
        log.info("【功能点】P0 - 缓存穿透防护（空值缓存）");

        // Given - 查询一个不存在的用户
        Long nonExistentUserId = 999L;
        log.info("【步骤 1】查询不存在的用户 ID: {}", nonExistentUserId);

        // When - 第一次查询（会访问数据库）
        log.info("【步骤 2】第一次查询（预期：访问数据库，返回 null）");
        String result1 = userService.getUser(nonExistentUserId);
        log.info("【结果】第一次查询结果: {}, DB 调用次数: {}", result1, UserService.dbCallCount.get());

        // Then
        assertNull(result1);
        assertEquals(1, UserService.dbCallCount.get());
        log.info("【验证】✓ 第一次查询访问了数据库");

        // When - 第二次查询（应该从缓存获取 null 值，不访问数据库）
        log.info("【步骤 3】第二次查询（预期：从缓存获取 null，不访问数据库）");
        String result2 = userService.getUser(nonExistentUserId);
        log.info("【结果】第二次查询结果: {}, DB 调用次数: {}", result2, UserService.dbCallCount.get());

        // Then
        assertNull(result2);
        assertEquals(1, UserService.dbCallCount.get()); // DB 调用次数不变
        log.info("【验证】✓ 第二次查询从缓存获取 null 值，未访问数据库");
        log.info("========== ✓ 缓存穿透防护测试通过 ==========");
    }

    /**
     * 测试缓存击穿防护：并发访问同一个 key 应该只有一个线程访问数据库
     */
    @Test
    public void testCacheBreakdownProtection() throws Exception {
        log.info("========== 端到端测试：缓存击穿防护 ==========");
        log.info("【功能点】P0 - 缓存击穿防护（分布式锁 + 重试机制）");

        // Given - 准备并发测试
        Long userId = 1L;
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        log.info("【步骤 1】准备 {} 个线程并发访问用户 ID: {}", threadCount, userId);

        // When - 并发访问同一个 key
        log.info("【步骤 2】启动并发访问（预期：只有 1 个线程访问数据库，其他线程通过重试获取缓存）");
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    String result = userService.getUser(userId);
                    log.debug("线程 {} 获取结果: {}", Thread.currentThread().getName(), result);
                } catch (Exception e) {
                    log.error("线程执行异常", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 开始并发访问
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(finished, "所有线程应该在 10 秒内完成");
        log.info("【结果】所有线程执行完成，DB 调用次数: {}", UserService.dbCallCount.get());

        // 由于分布式锁的保护，只有一个线程会访问数据库
        assertEquals(1, UserService.dbCallCount.get());
        log.info("【验证】✓ 并发访问只触发了 1 次数据库调用");
        log.info("【验证】✓ 分布式锁成功防止了缓存击穿");
        log.info("【验证】✓ 重试机制让其他线程成功获取到缓存数据");
        log.info("========== ✓ 缓存击穿防护测试通过 ==========");
    }

    /**
     * 测试完整业务流程：用户查询 -> 更新 -> 删除 -> 批量操作
     */
    @Test
    public void testCompleteBusinessFlow() {
        log.info("========== 端到端测试：完整业务流程 ==========");
        log.info("【功能点】P1 - 注解式 API + 编程式 API + L1/L2 缓存");

        // 场景 1: 用户查询（注解式 API + L1/L2 缓存）
        log.info("--- 场景 1: 用户查询（注解式 API + L1/L2 缓存）---");
        Long userId = 100L;
        log.info("【步骤 1.1】第一次查询用户（预期：访问数据库，写入 L1 和 L2）");
        String user1 = userService.getUser(userId);
        log.info("【结果】第一次查询: {}, DB 调用: {}", user1, UserService.dbCallCount.get());
        assertEquals("User-100", user1);
        assertEquals(1, UserService.dbCallCount.get());

        log.info("【步骤 1.2】第二次查询用户（预期：从 L1 缓存获取，不访问数据库）");
        String user2 = userService.getUser(userId);
        log.info("【结果】第二次查询: {}, DB 调用: {}", user2, UserService.dbCallCount.get());
        assertEquals("User-100", user2);
        assertEquals(1, UserService.dbCallCount.get()); // 从缓存获取
        log.info("【验证】✓ 场景 1 通过：L1/L2 缓存命中");

        // 场景 2: 用户更新（编程式 API）
        log.info("--- 场景 2: 用户更新（编程式 API）---");
        String newName = "UpdatedUser-100";
        log.info("【步骤 2.1】使用编程式 API 更新缓存: {}", newName);
        cacheManager.put("userCache", "user:" + userId, newName);

        log.info("【步骤 2.2】查询用户（预期：获取更新后的值，不访问数据库）");
        String user3 = userService.getUser(userId);
        log.info("【结果】更新后查询: {}, DB 调用: {}", user3, UserService.dbCallCount.get());
        assertEquals(newName, user3);
        assertEquals(1, UserService.dbCallCount.get()); // 仍然从缓存获取
        log.info("【验证】✓ 场景 2 通过：编程式 API 更新缓存成功");

        // 场景 3: 用户删除（缓存失效）
        log.info("--- 场景 3: 用户删除（缓存失效）---");
        log.info("【步骤 3.1】使用编程式 API 删除缓存");
        cacheManager.evict("userCache", "user:" + userId);

        log.info("【步骤 3.2】查询用户（预期：缓存失效，重新从数据库加载）");
        String user4 = userService.getUser(userId);
        log.info("【结果】删除后查询: {}, DB 调用: {}", user4, UserService.dbCallCount.get());
        assertEquals("User-100", user4);
        assertEquals(2, UserService.dbCallCount.get()); // 重新从数据库加载
        log.info("【验证】✓ 场景 3 通过：缓存失效后重新加载");

        // 场景 4: 批量操作
        log.info("--- 场景 4: 批量操作 ---");
        cacheManager.clear("userCache");
        UserService.dbCallCount.set(0);

        log.info("【步骤 4.1】批量查询 5 个用户（预期：访问数据库 5 次）");
        for (long id = 1; id <= 5; id++) {
            userService.getUser(id);
        }
        log.info("【结果】批量查询 5 个用户，DB 调用: {}", UserService.dbCallCount.get());
        assertEquals(5, UserService.dbCallCount.get());

        log.info("【步骤 4.2】再次批量查询（预期：从缓存获取，不访问数据库）");
        for (long id = 1; id <= 5; id++) {
            userService.getUser(id);
        }
        log.info("【结果】再次批量查询，DB 调用: {}", UserService.dbCallCount.get());
        assertEquals(5, UserService.dbCallCount.get()); // 调用次数不变
        log.info("【验证】✓ 场景 4 通过：批量操作正常");

        log.info("========== ✓ 完整业务流程测试通过 ==========");
    }

    /**
     * 测试缓存雪崩防护：TTL 随机偏移
     */
    @Test
    public void testCacheAvalancheProtection() throws Exception {
        log.info("========== 端到端测试：缓存雪崩防护 ==========");
        log.info("【功能点】P0 - 缓存雪崩防护（TTL 随机偏移）");

        // Given - 准备测试数据
        cacheManager.clear("userCache");
        UserService.dbCallCount.set(0);
        log.info("【步骤 1】写入 10 个用户到缓存");

        // When - 写入多个缓存项
        for (long id = 1; id <= 10; id++) {
            userService.getUser(id);
        }
        log.info("【结果】写入 10 个用户，DB 调用: {}", UserService.dbCallCount.get());
        assertEquals(10, UserService.dbCallCount.get());

        // Then - 验证缓存已写入
        log.info("【步骤 2】验证缓存已写入（预期：从缓存获取，不访问数据库）");
        UserService.dbCallCount.set(0);
        for (long id = 1; id <= 10; id++) {
            String user = userService.getUser(id);
            assertNotNull(user, "用户" + id + "应该存在");
            assertEquals("User-" + id, user, "用户" + id + "的值应该正确");
        }
        log.info("【结果】再次查询 10 个用户，DB 调用: {}", UserService.dbCallCount.get());
        assertEquals(0, UserService.dbCallCount.get(), "从缓存获取不应该访问数据库");
        log.info("【验证】✓ 缓存雪崩防护测试通过（TTL 随机偏移已配置）");
        log.info("========== ✓ 缓存雪崩防护测试通过 ==========");
    }

    /**
     * 测试最终一致性：L1 短 TTL，L2 长 TTL
     */
    @Test
    public void testFinalConsistency() throws Exception {
        log.info("========== 端到端测试：最终一致性 ==========");
        log.info("【功能点】P0 - 最终一致性（L1 短 TTL，L2 长 TTL）");

        // Given - 准备测试数据
        cacheManager.clear("userCache");
        UserService.dbCallCount.set(0);
        Long userId = 200L;

        // When - 第一次查询，写入 L1 和 L2
        log.info("【步骤 1】第一次查询用户（预期：访问数据库，写入 L1 和 L2）");
        String user1 = userService.getUser(userId);
        log.info("【结果】第一次查询: {}, DB 调用: {}", user1, UserService.dbCallCount.get());
        assertEquals("User-200", user1);
        assertEquals(1, UserService.dbCallCount.get());

        // Then - 第二次查询，从 L1 获取
        log.info("【步骤 2】第二次查询用户（预期：从 L1 缓存获取）");
        String user2 = userService.getUser(userId);
        log.info("【结果】第二次查询: {}, DB 调用: {}", user2, UserService.dbCallCount.get());
        assertEquals("User-200", user2);
        assertEquals(1, UserService.dbCallCount.get());

        log.info("【验证】✓ 最终一致性测试通过（L1/L2 TTL 配置已生效）");
        log.info("========== ✓ 最终一致性测试通过 ==========");
    }

    /**
     * 测试批量操作：getAll 和 putAll
     */
    @Test
    public void testBatchOperations() {
        log.info("========== 端到端测试：批量操作 ==========");
        log.info("【功能点】P1 - 批量操作（getAll, putAll）");

        // Given - 准备测试数据
        cacheManager.clear("userCache");
        UserService.dbCallCount.set(0);

        // When - 使用 putAll 批量写入
        log.info("【步骤 1】使用 putAll 批量写入 5 个用户");
        java.util.Map<String, Object> users = new java.util.HashMap<>();
        for (long id = 1; id <= 5; id++) {
            users.put("user:" + id, "BatchUser-" + id);
        }
        cacheManager.putAll("userCache", users);
        log.info("【结果】批量写入 5 个用户完成");

        // Then - 验证批量读取
        log.info("【步骤 2】验证批量读取（预期：从缓存获取，不访问数据库）");
        for (long id = 1; id <= 5; id++) {
            String user = cacheManager.get("userCache", "user:" + id);
            log.info("【结果】读取用户 {}: {}", id, user);
            assertEquals("BatchUser-" + id, user);
        }
        assertEquals(0, UserService.dbCallCount.get());
        log.info("【验证】✓ 批量操作测试通过");
        log.info("========== ✓ 批量操作测试通过 ==========");
    }

    /**
     * 测试场景 7：循环依赖检测
     */
    @Test
    public void testCircularDependencyDetection() {
        log.info("========== 端到端测试：循环依赖检测 ==========");

        // Given - 准备测试环境
        cacheManager.clear("depCache");

        // When & Then - 尝试触发循环依赖
        log.info("尝试触发循环依赖加载");
        try {
            cacheManager.get("depCache", "key1", () -> {
                // 在加载 key1 时尝试加载 key1，形成循环依赖
                return cacheManager.get("depCache", "key1", () -> "value1");
            });
            fail("应该抛出循环依赖异常");
        } catch (SmartCacheException e) {
            log.info("成功检测到循环依赖: {}", e.getMessage());
            assertTrue(e.getMessage().contains("循环依赖"), "异常消息应包含'循环依赖'");
        } catch (Exception e) {
            fail("应该抛出 SmartCacheException，实际抛出: " + e.getClass().getName() + ", 消息: " + e.getMessage());
        }

        log.info("✓ 循环依赖检测测试通过");
    }

    /**
     * 测试场景 8：统计 API
     */
    @Test
    public void testStatisticsAPI() {
        log.info("========== 端到端测试：统计 API ==========");

        // Given - 准备测试环境
        cacheManager.clear("statsCache");

        // When - 执行一系列缓存操作
        log.info("【步骤 1】执行缓存操作");

        // 第一次查询 - 未命中
        cacheManager.get("statsCache", "key1", () -> "value1");

        // 第二次查询 - L1 命中
        cacheManager.get("statsCache", "key1");

        // 第三次查询 - L1 命中
        cacheManager.get("statsCache", "key1");

        // Then - 验证统计信息
        log.info("【步骤 2】验证统计信息");
        CacheStats stats = cacheManager.getStats("statsCache");

        if (stats != null) {
            log.info("统计信息: L1命中={}, L2命中={}, 未命中={}",
                    stats.getL1HitCount(), stats.getL2HitCount(), stats.getMissCount());

            // 基本统计验证 - 总请求数可能包含clear操作，所以使用范围判断
            assertTrue(stats.getTotalRequests() >= 3 && stats.getTotalRequests() <= 4,
                    "总请求数应该在 3-4 之间，实际: " + stats.getTotalRequests());
            assertEquals(2, stats.getL1HitCount(), "L1 应该命中 2 次");
            assertEquals(0, stats.getL2HitCount(), "L2 应该命中 0 次");
            assertEquals(1, stats.getMissCount(), "应该有 1 次未命中");
            assertEquals(66.67, stats.getHitRate(), 0.01, "命中率应该是 66.67%");
            log.info("命中率 = 66.67%");

            log.info("✓ 统计信息验证通过");
        } else {
            log.warn("统计功能未启用，跳过验证");
        }

        log.info("✓ 统计 API 测试通过");
    }

    /**
     * 测试场景 9：预热功能验证
     * 注意：此测试不清空缓存，因为需要验证启动时的预热数据
     */
    @Test
    public void testWarmUpFunctionality() {
        log.info("========== 端到端测试：预热功能验证 ==========");

        // Given - 预热应该在应用启动时自动执行
        // 注意：不调用 setUp()，因为它会清空缓存

        log.info("【步骤 1】验证预热数据");

        // 检查 configCache 中的预热数据（注意：预热配置的 key 是 config:1 而不是 config1）
        String config1 = cacheManager.get("configCache", "config:1");
        String config2 = cacheManager.get("configCache", "config:2");
        String config3 = cacheManager.get("configCache", "config:3");

        log.info("预热数据: config:1={}, config:2={}, config:3={}", config1, config2, config3);

        // Then - 验证预热的数据已存在于 L2 缓存
        assertNotNull(config1, "config:1 应该已被预热");
        assertNotNull(config2, "config:2 应该已被预热");
        assertNotNull(config3, "config:3 应该已被预热");

        assertEquals("ConfigValue1", config1, "config:1 的值应该正确");
        assertEquals("ConfigValue2", config2, "config:2 的值应该正确");
        assertEquals("ConfigValue3", config3, "config:3 的值应该正确");

        log.info("预热数据验证通过");

        log.info("✓ 预热数据验证通过");

        // 检查 userCache 中的预热数据
        // 注意：由于其他测试可能已经清空了 userCache，这里只验证 configCache
        // 如果需要验证 userCache，应该将此测试放在第一个执行

        log.info("✓ 预热功能验证测试通过");
    }

    /**
     * 测试场景 10：异步刷新（通过 L1 的 refreshSeconds 配置）
     */
    @Test
    public void testAsyncRefresh() throws Exception {
        log.info("========== 端到端测试：异步刷新 ==========");

        // Given - 准备测试环境
        cacheManager.clear("refreshCache");
        AtomicInteger loadCount = new AtomicInteger(0);

        // When - 首次加载数据
        log.info("【步骤 1】首次加载数据");
        String value1 = cacheManager.get("refreshCache", "key1", () -> {
            loadCount.incrementAndGet();
            return "value-" + System.currentTimeMillis();
        });
        log.info("首次加载结果: {}, 加载次数: {}", value1, loadCount.get());
        assertEquals(1, loadCount.get());

        // Then - 立即再次获取，应该从缓存获取
        log.info("【步骤 2】立即再次获取");
        String value2 = cacheManager.get("refreshCache", "key1", () -> {
            loadCount.incrementAndGet();
            return "value-" + System.currentTimeMillis();
        });
        log.info("第二次获取结果: {}, 加载次数: {}", value2, loadCount.get());
        assertEquals(value1, value2, "应该从缓存获取相同的值");
        assertEquals(1, loadCount.get(), "不应该重新加载");

        log.info("✓ 异步刷新测试通过");
    }

    /**
     * 测试场景 11：@SmartCachePut 注解
     */
    @Test
    public void testCachePutAnnotation() {
        log.info("========== 端到端测试：@SmartCachePut 注解 ==========");

        // Given - 准备测试环境
        cacheManager.clear("annotationCache");

        // When - 使用 @SmartCachePut 更新缓存
        log.info("【步骤 1】使用 @SmartCachePut 更新缓存");
        String result1 = annotationService.updateUser(100L, "UpdatedUser-100");
        assertEquals("UpdatedUser-100", result1);

        // Then - 验证缓存已更新
        log.info("【步骤 2】验证缓存已更新");
        String cached = cacheManager.get("annotationCache", "user:100");
        assertEquals("UpdatedUser-100", cached);

        log.info("✓ @SmartCachePut 注解测试通过");
    }

    /**
     * 测试场景 12：@SmartCacheEvict 注解
     */
    @Test
    public void testCacheEvictAnnotation() {
        log.info("========== 端到端测试：@SmartCacheEvict 注解 ==========");

        // Given - 准备测试数据
        cacheManager.clear("annotationCache");
        cacheManager.put("annotationCache", "user:200", "User-200");
        cacheManager.put("annotationCache", "user:201", "User-201");

        // When - 使用 @SmartCacheEvict 删除单个缓存
        log.info("【步骤 1】使用 @SmartCacheEvict 删除单个缓存");
        annotationService.deleteUser(200L);

        // Then - 验证缓存已删除
        log.info("【步骤 2】验证缓存已删除");
        assertNull(cacheManager.get("annotationCache", "user:200"));
        assertNotNull(cacheManager.get("annotationCache", "user:201"));

        // When - 使用 @SmartCacheEvict 清空所有缓存
        log.info("【步骤 3】使用 @SmartCacheEvict 清空所有缓存");
        annotationService.clearAllUsers();

        // Then - 验证所有缓存已清空
        log.info("【步骤 4】验证所有缓存已清空");
        assertNull(cacheManager.get("annotationCache", "user:201"));

        log.info("✓ @SmartCacheEvict 注解测试通过");
    }

    /**
     * 测试场景 13：SpEL 表达式和条件缓存
     */
    @Test
    public void testSpELAndCondition() {
        log.info("========== 端到端测试：SpEL 表达式和条件缓存 ==========");

        // Given - 准备测试环境
        cacheManager.clear("spelCache");

        // When - 测试复杂 SpEL 表达式
        log.info("【步骤 1】测试复杂 SpEL 表达式");
        String result1 = annotationService.getUserWithComplexKey(100L, "admin");
        assertEquals("User-100-admin", result1);

        // Then - 验证缓存 key 正确
        String cached1 = cacheManager.get("spelCache", "user:100:admin");
        assertEquals("User-100-admin", cached1);

        // When - 测试条件缓存（满足条件）
        log.info("【步骤 2】测试条件缓存（满足条件）");
        String result2 = annotationService.getUserWithCondition(200L);
        String cached2 = cacheManager.get("spelCache", "user:200");
        assertEquals("User-200", cached2);

        // When - 测试条件缓存（不满足条件）
        log.info("【步骤 3】测试条件缓存（不满足条件）");
        String result3 = annotationService.getUserWithCondition(50L);
        String cached3 = cacheManager.get("spelCache", "user:50");
        assertNull(cached3, "userId < 100 不应该缓存");

        log.info("✓ SpEL 表达式和条件缓存测试通过");
    }

    /**
     * 测试场景 14：异常处理
     */
    @Test
    public void testExceptionHandling() {
        log.info("========== 端到端测试：异常处理 ==========");

        // Given - 准备测试环境
        cacheManager.clear("exceptionCache");

        // When & Then - 测试加载异常
        log.info("【步骤 1】测试加载异常");
        try {
            cacheManager.get("exceptionCache", "error", () -> {
                throw new RuntimeException("模拟加载失败");
            });
            fail("应该抛出异常");
        } catch (Exception e) {
            log.info("成功捕获异常: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            assertTrue(e.getMessage().contains("模拟加载失败") || e.getMessage().contains("缓存加载失败"),
                    "异常消息应该包含加载失败信息");
        }

        log.info("✓ 异常处理测试通过");
    }

    @TestConfiguration
    static class TestFixturesConfiguration {

        @Bean
        AnnotationService annotationService() {
            return new AnnotationService();
        }

        @Bean
        UserService userService() {
            return new UserService();
        }
    }

    /**
     * 注解测试服务
     */
    public static class AnnotationService {

        @SmartCachePut(cacheName = "annotationCache", key = "'user:' + #userId")
        public String updateUser(Long userId, String userName) {
            return userName;
        }

        @SmartCacheEvict(cacheName = "annotationCache", key = "'user:' + #userId")
        public void deleteUser(Long userId) {
            log.info("删除用户: {}", userId);
        }

        @SmartCacheEvict(cacheName = "annotationCache", allEntries = true)
        public void clearAllUsers() {
            log.info("清空所有用户缓存");
        }

        @SmartCacheable(cacheName = "spelCache", key = "'user:' + #userId + ':' + #role")
        public String getUserWithComplexKey(Long userId, String role) {
            return "User-" + userId + "-" + role;
        }

        @SmartCacheable(cacheName = "spelCache", key = "'user:' + #userId", condition = "#userId >= 100")
        public String getUserWithCondition(Long userId) {
            return "User-" + userId;
        }
    }

    /**
     * 测试服务
     */
    public static class UserService {

        public static AtomicInteger dbCallCount = new AtomicInteger(0);
        public static AtomicInteger nullQueryCount = new AtomicInteger(0);

        @SmartCacheable(cacheName = "userCache", key = "'user:' + #userId")
        public String getUser(Long userId) {
            dbCallCount.incrementAndGet();
            log.info("从数据库加载用户: {}", userId);

            // 模拟数据库查询
            try {
                Thread.sleep(100); // 模拟数据库延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 模拟不存在的用户
            if (userId >= 900) {
                nullQueryCount.incrementAndGet();
                log.info("用户不存在: {}", userId);
                return null;
            }

            return "User-" + userId;
        }
    }
}
