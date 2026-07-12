package io.github.surezzzzzz.sdk.lock.redis.cases;

import io.github.surezzzzzz.sdk.lock.redis.LockApplication;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁默认单 Redis 模式端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = LockApplication.class)
public class SimpleRedisLockTest {

    private static final String LOCK_KEY = "test:lock:simple";
    private static final String LOCK_VALUE = "test-client-id";
    private static final String WRONG_VALUE = "test-client-wrong";
    private static final List<String> TEST_KEYS = Arrays.asList(
            LOCK_KEY,
            "test:lock:mutex",
            "test:lock:expire",
            "test:lock:concurrent"
    );

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    @Autowired
    private StringRedisTemplate simpleRedisLockRedisTemplate;

    @AfterEach
    public void cleanUp() {
        simpleRedisLockRedisTemplate.delete(TEST_KEYS);
    }

    @Test
    public void testTryLockSuccessAndDuplicateFails() {
        log.info("验证默认模式加锁成功后重复加锁失败，lockKey={}", LOCK_KEY);
        boolean first = simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS);
        assertTrue(first, "第一次加锁应成功");
        assertEquals(LOCK_VALUE, simpleRedisLockRedisTemplate.opsForValue().get(LOCK_KEY), "Redis 中锁 value 必须匹配持有者");
        Long ttl = simpleRedisLockRedisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS);
        log.info("默认模式加锁后 TTL=[{}] 秒", ttl);
        assertNotNull(ttl, "TTL 不应为 null");
        assertTrue(ttl > 0 && ttl <= 10, "锁 TTL 必须在有效范围内");

        boolean second = simpleRedisLock.tryLock(LOCK_KEY, WRONG_VALUE, 10, TimeUnit.SECONDS);
        assertFalse(second, "重复加锁应失败");
        assertEquals(LOCK_VALUE, simpleRedisLockRedisTemplate.opsForValue().get(LOCK_KEY), "重复加锁失败后原 value 不应变化");
    }

    @Test
    public void testUnlockWithOwnerValueDeletesLock() {
        log.info("验证正确 lockValue 解锁会删除锁，lockKey={}", LOCK_KEY);
        assertTrue(simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "加锁应成功");
        boolean unlocked = simpleRedisLock.unlock(LOCK_KEY, LOCK_VALUE);
        assertTrue(unlocked, "正确 lockValue 解锁应返回 true");
        assertFalse(Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LOCK_KEY)), "正确解锁后 key 应被删除");
    }

    @Test
    public void testUnlockWithWrongValueKeepsLock() {
        log.info("验证错误 lockValue 解锁不会删除锁，lockKey={}", LOCK_KEY);
        assertTrue(simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "加锁应成功");
        boolean unlocked = simpleRedisLock.unlock(LOCK_KEY, WRONG_VALUE);
        assertFalse(unlocked, "错误 lockValue 解锁应返回 false");
        assertEquals(LOCK_VALUE, simpleRedisLockRedisTemplate.opsForValue().get(LOCK_KEY), "错误解锁后锁仍应存在");
    }

    @Test
    public void testLockExpirationAllowsRelock() throws Exception {
        String lockKey = "test:lock:expire";
        log.info("验证锁过期后可重新加锁，lockKey={}", lockKey);
        assertTrue(simpleRedisLock.tryLock(lockKey, LOCK_VALUE, 1, TimeUnit.SECONDS), "首次加锁应成功");
        Thread.sleep(1500L);
        assertTrue(simpleRedisLock.tryLock(lockKey, WRONG_VALUE, 10, TimeUnit.SECONDS), "锁过期后重新加锁应成功");
        assertEquals(WRONG_VALUE, simpleRedisLockRedisTemplate.opsForValue().get(lockKey), "重新加锁后 value 应更新为新持有者");
    }

    @Test
    public void testConcurrentLockOnlyOneOwner() throws Exception {
        String lockKey = "test:lock:concurrent";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean locked = simpleRedisLock.tryLock(lockKey, "client-" + threadId, 10, TimeUnit.SECONDS);
                    if (locked) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("并发加锁线程异常，threadId={}", threadId, e);
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "并发测试线程必须在 10 秒内结束");
        executor.shutdownNow();
        log.info("并发加锁完成，成功次数={}", successCount.get());
        assertEquals(1, successCount.get(), "同一个 lockKey 并发竞争时只能有一个持有者");
        assertNotNull(simpleRedisLockRedisTemplate.opsForValue().get(lockKey), "成功加锁后 Redis 中必须存在锁 value");
    }
}
