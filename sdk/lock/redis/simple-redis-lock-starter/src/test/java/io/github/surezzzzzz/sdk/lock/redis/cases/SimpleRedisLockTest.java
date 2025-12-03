package io.github.surezzzzzz.sdk.lock.redis.cases;

import io.github.surezzzzzz.sdk.lock.redis.LockApplication;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author: Sure.
 * @description: Redis分布式锁测试类
 * @Date: 2024/12/3 14:01
 */
@Slf4j
@SpringBootTest(classes = LockApplication.class)
public class SimpleRedisLockTest {

    private RedisServer redisServer;
    private static int redisPort;

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        redisPort = findAvailablePort();
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> redisPort);
    }

    @BeforeEach
    void setUp() throws Exception {
        try {
            redisServer = new RedisServer(redisPort);
            redisServer.start();
        } catch (Exception e) {
            redisPort = findAvailablePort();
            redisServer = new RedisServer(redisPort);
            redisServer.start();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (redisServer != null) {
            try {
                redisServer.stop();
            } catch (Exception e) {
                // 忽略停止时的异常
            }
        }
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 6380 + (int) (Math.random() * 1000);
        }
    }

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    private static final String TEST_LOCK_KEY = "test:lock:simple";
    private static final String TEST_LOCK_VALUE = "test-client-id";
    private static final long EXPIRE_TIME = 10;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @Test
//    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void smokeTest() throws Exception {
        log.info("开始Redis分布式锁基础功能测试");

        // 测试1: 基础加锁解锁
        testBasicLockUnlock();

        // 测试2: 锁的互斥性
        testLockMutualExclusion();

        // 测试3: 锁的过期机制
        testLockExpiration();

        // 测试4: 并发竞争测试
        testConcurrentLockCompetition();

        log.info("Redis分布式锁基础功能测试完成");
    }

    private void testBasicLockUnlock() {
        log.info("=== 测试1: 基础加锁解锁 ===");

        // 尝试加锁
        boolean lockResult = simpleRedisLock.tryLock(TEST_LOCK_KEY, TEST_LOCK_VALUE, EXPIRE_TIME, TIME_UNIT);
        assertTrue(lockResult, "加锁应该成功");
        log.info("成功获取锁: key={}, value={}", TEST_LOCK_KEY, TEST_LOCK_VALUE);

        // 再次尝试加锁（应该失败）
        boolean secondLockResult = simpleRedisLock.tryLock(TEST_LOCK_KEY, "other-value", EXPIRE_TIME, TIME_UNIT);
        assertFalse(secondLockResult, "重复加锁应该失败");
        log.info("重复加锁失败，符合预期");

        // 释放锁
        simpleRedisLock.unlock(TEST_LOCK_KEY, TEST_LOCK_VALUE);
        log.info("成功释放锁: key={}, value={}", TEST_LOCK_KEY, TEST_LOCK_VALUE);

        // 验证锁已释放（重新加锁应该成功）
        boolean afterUnlockResult = simpleRedisLock.tryLock(TEST_LOCK_KEY, "new-value", EXPIRE_TIME, TIME_UNIT);
        assertTrue(afterUnlockResult, "锁释放后重新加锁应该成功");
        log.info("锁释放后重新加锁成功");

        // 清理
        simpleRedisLock.unlock(TEST_LOCK_KEY, "new-value");
        log.info("基础加锁解锁测试通过");
    }

    private void testLockMutualExclusion() throws InterruptedException {
        log.info("=== 测试2: 锁的互斥性 ===");

        String mutexKey = "test:mutex:lock";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 线程1
        executor.submit(() -> {
            try {
                startLatch.await();
                boolean locked = simpleRedisLock.tryLock(mutexKey, "client1", EXPIRE_TIME, TIME_UNIT);
                if (locked) {
                    successCount.incrementAndGet();
                    log.info("线程1获取锁成功");
                    Thread.sleep(1000); // 持有锁1秒
                    simpleRedisLock.unlock(mutexKey, "client1");
                    log.info("线程1释放锁");
                } else {
                    log.info("线程1获取锁失败");
                }
            } catch (Exception e) {
                log.error("线程1执行异常", e);
            } finally {
                completeLatch.countDown();
            }
        });

        // 线程2
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100); // 稍微延迟，确保线程1先尝试
                boolean locked = simpleRedisLock.tryLock(mutexKey, "client2", EXPIRE_TIME, TIME_UNIT);
                if (locked) {
                    successCount.incrementAndGet();
                    log.info("线程2获取锁成功");
                    simpleRedisLock.unlock(mutexKey, "client2");
                    log.info("线程2释放锁");
                } else {
                    log.info("线程2获取锁失败");
                }
            } catch (Exception e) {
                log.error("线程2执行异常", e);
            } finally {
                completeLatch.countDown();
            }
        });

        // 启动测试
        startLatch.countDown();
        completeLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "互斥测试中应该只有一个线程能获取锁");
        log.info("锁的互斥性测试通过");
    }

    private void testLockExpiration() throws InterruptedException {
        log.info("=== 测试3: 锁的过期机制 ===");

        String expireKey = "test:expire:lock";
        String expireValue = "expire-test";

        // 加锁，设置较短的过期时间
        boolean locked = simpleRedisLock.tryLock(expireKey, expireValue, 2, TimeUnit.SECONDS);
        assertTrue(locked, "加锁应该成功");
        log.info("获取锁成功，设置2秒过期时间");

        // 等待锁过期
        log.info("等待锁过期...");
        Thread.sleep(2500);

        // 尝试再次加锁，应该成功（因为原锁已过期）
        boolean afterExpireResult = simpleRedisLock.tryLock(expireKey, "new-client", EXPIRE_TIME, TIME_UNIT);
        assertTrue(afterExpireResult, "锁过期后重新加锁应该成功");
        log.info("锁过期后重新加锁成功");

        // 清理
        simpleRedisLock.unlock(expireKey, "new-client");
        log.info("锁过期机制测试通过");
    }

    private void testConcurrentLockCompetition() throws InterruptedException {
        log.info("=== 测试4: 并发竞争测试 ===");

        String concurrentKey = "test:concurrent:lock";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger totalAttempts = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    totalAttempts.incrementAndGet();

                    String clientId = "client-" + threadId;
                    boolean locked = simpleRedisLock.tryLock(concurrentKey, clientId, EXPIRE_TIME, TIME_UNIT);

                    if (locked) {
                        successCount.incrementAndGet();
                        log.info("线程{}获取锁成功", threadId);
                        Thread.sleep(100); // 模拟业务处理
                        simpleRedisLock.unlock(concurrentKey, clientId);
                        log.info("线程{}释放锁", threadId);
                    } else {
                        log.info("线程{}获取锁失败", threadId);
                    }
                } catch (Exception e) {
                    log.error("线程{}执行异常", threadId, e);
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // 启动测试
        startLatch.countDown();
        completeLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("并发测试完成: 总尝试次数={}, 成功次数={}", totalAttempts.get(), successCount.get());
        assertTrue(successCount.get() > 0, "至少应该有一个线程能获取锁");
        assertTrue(successCount.get() < threadCount, "不可能所有线程都同时获取锁");
        log.info("并发竞争测试通过");
    }
}