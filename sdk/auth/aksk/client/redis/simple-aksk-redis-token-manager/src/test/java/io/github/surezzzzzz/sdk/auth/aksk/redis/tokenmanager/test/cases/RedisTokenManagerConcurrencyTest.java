package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

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
 *
 * <p>验证 SmartCacheManager 内置分布式锁在并发场景下的表现
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
class RedisTokenManagerConcurrencyTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    @BeforeEach
    void setUp() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
    }

    private void cleanupTestKeys() {
        Set<String> keys = stringRedisTemplate.keys("sure-auth-aksk-client:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("测试并发获取 Token - 10个线程同时获取")
    void testConcurrentGetToken() throws InterruptedException {
        log.info("========== 测试并发获取 Token - 10个线程同时获取 ==========");

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        ConcurrentHashMap<Integer, String> tokenMap = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    String token = tokenManager.getToken();
                    tokenMap.put(threadId, token);
                    successCount.incrementAndGet();
                    log.info("线程 {} 获取 Token 成功", threadId);
                } catch (Exception e) {
                    log.error("线程 {} 获取 Token 失败", threadId, e);
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        log.info("成功: {}, 失败: {}", successCount.get(), errorCount.get());

        assertEquals(threadCount, successCount.get(), "所有线程都应成功获取 Token");
        assertEquals(0, errorCount.get(), "不应有失败的线程");

        String firstToken = tokenMap.get(0);
        assertNotNull(firstToken, "第一个线程应获取到 Token");
        for (int i = 1; i < threadCount; i++) {
            assertEquals(firstToken, tokenMap.get(i), "所有线程获取的 Token 应相同");
        }

        log.info("✓ 10线程并发获取 Token 一致");
    }

    @Test
    @DisplayName("测试并发获取 Token - 50个线程高并发")
    void testHighConcurrentGetToken() throws InterruptedException {
        log.info("========== 测试并发获取 Token - 50个线程高并发 ==========");

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ConcurrentHashMap<String, Integer> tokenCountMap = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    String token = tokenManager.getToken();
                    tokenCountMap.merge(token, 1, Integer::sum);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("线程 {} 获取 Token 失败", threadId, e);
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        log.info("成功: {}, 失败: {}, 不同 Token 数: {}", successCount.get(), errorCount.get(), tokenCountMap.size());

        assertEquals(threadCount, successCount.get(), "所有线程都应成功获取 Token");
        assertEquals(0, errorCount.get(), "不应有失败的线程");
        assertEquals(1, tokenCountMap.size(), "所有线程应获取同一个 Token（分布式锁生效）");

        log.info("✓ 50线程高并发获取 Token 一致");
    }

    @Test
    @DisplayName("测试并发清除和获取 Token")
    void testConcurrentClearAndGetToken() throws InterruptedException {
        log.info("========== 测试并发清除和获取 Token ==========");

        String initialToken = tokenManager.getToken();
        assertNotNull(initialToken, "初始 Token 不应为 null");
        log.info("初始 Token: {}", initialToken);

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentHashMap<Integer, String> tokenMap = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            final boolean isClearThread = i < 10;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    if (isClearThread) {
                        tokenManager.clearToken();
                    } else {
                        String token = tokenManager.getToken();
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

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        log.info("成功操作数: {}, 获取 Token 线程数: {}", successCount.get(), tokenMap.size());

        assertEquals(threadCount, successCount.get(), "所有 20 个操作（10 clear + 10 get）都应成功");
        assertEquals(10, tokenMap.size(), "10 个 get 线程都应成功获取 Token");
        for (String token : tokenMap.values()) {
            assertNotNull(token, "获取的 Token 不应为 null");
            assertTrue(token.startsWith("eyJ"), "获取的 Token 应为 JWT 格式");
            assertEquals(3, token.split("\\.").length, "Token 应为有效 JWT（3 个部分）");
        }

        log.info("✓ 并发清除和获取场景下没有异常");
    }

    @Test
    @DisplayName("测试并发安全性 - 所有线程共享同一 SecurityContext 时获取相同 Token")
    void testConcurrentGetTokenWithSameSecurityContext() throws InterruptedException {
        log.info("========== 测试并发安全性 ==========");

        int userCount = 3;
        int threadsPerUser = 10;
        int totalThreads = userCount * threadsPerUser;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);

        ConcurrentHashMap<Integer, Set<String>> userTokensMap = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int userId = 0; userId < userCount; userId++) {
            final int currentUserId = userId;
            userTokensMap.put(currentUserId, ConcurrentHashMap.newKeySet());

            for (int threadId = 0; threadId < threadsPerUser; threadId++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        String token = tokenManager.getToken();
                        userTokensMap.get(currentUserId).add(token);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("用户 {} 获取 Token 失败", currentUserId, e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        assertEquals(totalThreads, successCount.get(), "所有线程都应成功获取 Token");

        // 默认 SecurityContextProvider 返回 null，所有用户共享同一 cacheKey
        String cacheKey = StringUtils.hasText(securityContextProvider.getSecurityContext())
                ? String.valueOf(securityContextProvider.getSecurityContext().hashCode())
                : "default";
        assertEquals("default", cacheKey, "默认 SecurityContextProvider 应使用 default cacheKey");

        Set<String> allTokens = userTokensMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        assertEquals(1, allTokens.size(), "所有线程应获取同一个 Token");

        log.info("✓ 并发安全性验证通过，cacheKey={}", cacheKey);
    }
}
