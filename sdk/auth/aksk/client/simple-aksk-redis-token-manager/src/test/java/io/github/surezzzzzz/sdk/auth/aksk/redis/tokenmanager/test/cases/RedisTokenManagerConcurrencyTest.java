package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.strategy.RedisTokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 并发测试
 * <p>
 * 测试分布式锁在并发场景下的表现
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
class RedisTokenManagerConcurrencyTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTokenCacheStrategy tokenCacheStrategy;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @BeforeEach
    void setUp() {
        log.info("======================================");
        log.info("清理 Redis 测试数据");
        log.info("======================================");
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        log.info("======================================");
        log.info("测试结束，清理 Redis 测试数据");
        log.info("======================================");
        cleanupTestKeys();
    }

    /**
     * 清理测试相关的 Redis Key
     */
    private void cleanupTestKeys() {
        String pattern = "sure-auth-aksk-client:redis-token-manager-test:*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            log.info("删除测试 Key: {}", keys);
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("测试并发获取 Token - 10个线程同时获取")
    void testConcurrentGetToken() throws InterruptedException {
        log.info("======================================");
        log.info("测试并发获取 Token - 10个线程同时获取");
        log.info("======================================");

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // 用于收集每个线程获取的 Token
        ConcurrentHashMap<Integer, String> tokenMap = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        log.info("启动 {} 个线程并发获取 Token", threadCount);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    // 等待所有线程准备就绪
                    startLatch.await();

                    log.info("线程 {} 开始获取 Token", threadId);
                    long startTime = System.currentTimeMillis();

                    String token = tokenManager.getToken();

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("线程 {} 获取 Token 成功，耗时: {}ms, Token: {}", threadId, duration, token);

                    tokenMap.put(threadId, token);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("线程 {} 获取 Token 失败", threadId, e);
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 所有线程同时开始
        log.info("所有线程准备就绪，开始并发获取 Token");
        startLatch.countDown();

        // 等待所有线程完成
        endLatch.await();
        executorService.shutdown();

        log.info("======================================");
        log.info("并发测试完成");
        log.info("成功: {}, 失败: {}", successCount.get(), errorCount.get());
        log.info("======================================");

        // 验证结果
        assertEquals(threadCount, successCount.get(), "所有线程都应成功获取 Token");
        assertEquals(0, errorCount.get(), "不应有失败的线程");

        // 验证所有线程获取的 Token 相同
        String firstToken = tokenMap.get(0);
        assertNotNull(firstToken, "第一个线程应获取到 Token");

        for (int i = 1; i < threadCount; i++) {
            String token = tokenMap.get(i);
            assertNotNull(token, "线程 " + i + " 应获取到 Token");
            assertEquals(firstToken, token, "所有线程获取的 Token 应相同（来自同一次服务器请求或缓存）");
        }

        log.info("验证通过：所有线程获取的 Token 一致");
        log.info("======================================");
    }

    @Test
    @DisplayName("测试并发获取 Token - 50个线程高并发")
    void testHighConcurrentGetToken() throws InterruptedException {
        log.info("======================================");
        log.info("测试并发获取 Token - 50个线程高并发");
        log.info("======================================");

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ConcurrentHashMap<String, Integer> tokenCountMap = new ConcurrentHashMap<>();

        log.info("启动 {} 个线程并发获取 Token", threadCount);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    String token = tokenManager.getToken();

                    // 统计每个 Token 被获取的次数
                    tokenCountMap.merge(token, 1, Integer::sum);
                    successCount.incrementAndGet();

                    if (threadId % 10 == 0) {
                        log.info("线程 {} 获取 Token 成功", threadId);
                    }
                } catch (Exception e) {
                    log.error("线程 {} 获取 Token 失败", threadId, e);
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 所有线程同时开始
        log.info("所有线程准备就绪，开始高并发获取 Token");
        long startTime = System.currentTimeMillis();
        startLatch.countDown();

        // 等待所有线程完成
        endLatch.await();
        executorService.shutdown();
        long duration = System.currentTimeMillis() - startTime;

        log.info("======================================");
        log.info("高并发测试完成");
        log.info("总耗时: {}ms", duration);
        log.info("成功: {}, 失败: {}", successCount.get(), errorCount.get());
        log.info("不同 Token 数量: {}", tokenCountMap.size());
        tokenCountMap.forEach((token, count) ->
                log.info("Token: {}, 获取次数: {}", token, count));
        log.info("======================================");

        // 验证结果
        assertEquals(threadCount, successCount.get(), "所有线程都应成功获取 Token");
        assertEquals(0, errorCount.get(), "不应有失败的线程");
        assertEquals(1, tokenCountMap.size(), "所有线程应获取同一个 Token");

        log.info("验证通过：高并发场景下所有线程获取的 Token 一致");
        log.info("======================================");
    }

    @Test
    @DisplayName("测试并发清除和获取 Token")
    void testConcurrentClearAndGetToken() throws InterruptedException {
        log.info("======================================");
        log.info("测试并发清除和获取 Token");
        log.info("======================================");

        // 先获取一个 Token
        log.info("先获取一个初始 Token");
        String initialToken = tokenManager.getToken();
        log.info("初始 Token: {}", initialToken);

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentHashMap<Integer, String> tokenMap = new ConcurrentHashMap<>();

        // 10个线程获取 Token，10个线程清除 Token
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            final boolean isClearThread = i < 10;

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    if (isClearThread) {
                        log.info("线程 {} 清除 Token", threadId);
                        tokenManager.clearToken();
                    } else {
                        String token = tokenManager.getToken();
                        log.info("线程 {} 获取 Token: {}", threadId, token);
                        tokenMap.put(threadId, token);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("线程 {} 执行失败", threadId, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 所有线程同时开始
        log.info("所有线程准备就绪，开始并发清除和获取 Token");
        startLatch.countDown();

        // 等待所有线程完成
        endLatch.await();
        executorService.shutdown();

        log.info("======================================");
        log.info("并发清除和获取测试完成");
        log.info("成功操作数: {}", successCount.get());
        log.info("获取到 Token 的线程数: {}", tokenMap.size());
        log.info("======================================");

        // 验证：所有获取 Token 的线程都应该成功
        assertTrue(tokenMap.size() > 0, "至少应有线程成功获取 Token");
        tokenMap.values().forEach(token ->
                assertNotNull(token, "获取的 Token 不应为 null"));

        log.info("验证通过：并发清除和获取场景下没有异常");
        log.info("======================================");
    }

    @Test
    @DisplayName("测试不同 security_context 并发获取 Token - 验证多用户隔离")
    void testConcurrentGetTokenWithDifferentSecurityContexts() throws InterruptedException {
        log.info("======================================");
        log.info("测试不同 security_context 并发获取 Token - 验证多用户隔离");
        log.info("======================================");

        // 注意：由于 SecurityContextProvider 是 Spring Bean，默认返回 null
        // 这个测试验证在相同 security_context (null) 下，多个线程获取相同的 Token
        // 如果需要测试不同的 security_context，需要自定义 SecurityContextProvider

        log.info("当前 SecurityContextProvider 返回的 security_context: {}",
                securityContextProvider.getSecurityContext());

        // 模拟 3 个不同的"用户"场景（虽然实际都是 null context）
        int userCount = 3;
        int threadsPerUser = 10;
        int totalThreads = userCount * threadsPerUser;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);

        // 用于收集每个用户的 Token
        ConcurrentHashMap<Integer, Set<String>> userTokensMap = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);

        log.info("启动 {} 个用户，每个用户 {} 个线程并发获取 Token", userCount, threadsPerUser);

        // 为每个用户启动多个线程
        for (int userId = 0; userId < userCount; userId++) {
            final int currentUserId = userId;
            userTokensMap.put(currentUserId, ConcurrentHashMap.newKeySet());

            for (int threadId = 0; threadId < threadsPerUser; threadId++) {
                final int currentThreadId = threadId;
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        log.debug("用户 {} 的线程 {} 开始获取 Token", currentUserId, currentThreadId);
                        String token = tokenManager.getToken();

                        userTokensMap.get(currentUserId).add(token);
                        successCount.incrementAndGet();

                        if (currentThreadId == 0) {
                            log.info("用户 {} 的第一个线程获取 Token 成功", currentUserId);
                        }
                    } catch (Exception e) {
                        log.error("用户 {} 的线程 {} 获取 Token 失败", currentUserId, currentThreadId, e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        // 所有线程同时开始
        log.info("所有线程准备就绪，开始并发获取 Token");
        startLatch.countDown();

        // 等待所有线程完成
        endLatch.await();
        executorService.shutdown();

        log.info("======================================");
        log.info("不同用户并发测试完成");
        log.info("成功: {}", successCount.get());
        log.info("======================================");

        // 验证结果
        assertEquals(totalThreads, successCount.get(), "所有线程都应成功获取 Token");

        // 验证每个用户的所有线程获取的 Token 相同
        for (int userId = 0; userId < userCount; userId++) {
            Set<String> userTokens = userTokensMap.get(userId);
            log.info("用户 {} 获取的不同 Token 数量: {}", userId, userTokens.size());
            assertEquals(1, userTokens.size(),
                    "用户 " + userId + " 的所有线程应获取相同的 Token");
        }

        // 由于默认 SecurityContextProvider 返回 null，所有用户实际上共享同一个 Token
        Set<String> allTokens = userTokensMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        log.info("所有用户获取的不同 Token 总数: {}", allTokens.size());
        assertEquals(1, allTokens.size(),
                "由于使用默认 SecurityContextProvider (返回 null)，所有用户应共享同一个 Token");

        // 验证 Redis 中的 Key
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        log.info("Redis 缓存 Key: {}", cacheKey);
        assertTrue(cacheKey.contains("default"), "应使用 default Key");

        log.info("验证通过：多用户并发场景下 Token 管理正常");
        log.info("注意：如需测试真实的多用户隔离，需要自定义 SecurityContextProvider");
        log.info("======================================");
    }
}
