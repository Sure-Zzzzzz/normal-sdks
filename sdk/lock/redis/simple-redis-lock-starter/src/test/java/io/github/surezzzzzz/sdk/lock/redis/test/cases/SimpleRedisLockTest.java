package io.github.surezzzzzz.sdk.lock.redis.test.cases;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorCode;
import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.lock.redis.exception.ValidationException;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.lock.redis.test.SimpleRedisLockTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
@SpringBootTest(classes = SimpleRedisLockTestApplication.class)
public class SimpleRedisLockTest {

    private static final String LOCK_KEY = "test:lock:simple";
    private static final String LOCK_VALUE = "test-client-id";
    private static final String WRONG_VALUE = "test-client-wrong";
    private static final String LEASE_RENEW_KEY = "test:lock:lease:renew";
    private static final String LEASE_EXPIRE_KEY = "test:lock:lease:expire";
    private static final String LEASE_STALE_KEY = "test:lock:lease:stale";
    private static final String LEASE_RELEASE_KEY = "test:lock:lease:release";
    private static final String LEASE_COMPETITION_KEY = "test:lock:lease:competition";
    private static final String LEASE_CLOSE_KEY = "test:lock:lease:close";
    private static final String LEASE_INVALID_TIME_KEY = "test:lock:lease:invalid-time";
    private static final List<String> TEST_KEYS = Arrays.asList(
            LOCK_KEY,
            "test:lock:mutex",
            "test:lock:expire",
            "test:lock:concurrent",
            LEASE_RENEW_KEY,
            LEASE_EXPIRE_KEY,
            LEASE_STALE_KEY,
            LEASE_RELEASE_KEY,
            LEASE_COMPETITION_KEY,
            LEASE_CLOSE_KEY,
            LEASE_INVALID_TIME_KEY
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
        log.info("第一次加锁结果={}", first);
        assertTrue(first, "第一次加锁应成功");
        String lockValue = simpleRedisLockRedisTemplate.opsForValue().get(LOCK_KEY);
        Long ttl = simpleRedisLockRedisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS);
        log.info("默认模式加锁后 Redis value 存在={}，TTL=[{}] 秒", lockValue != null, ttl);
        assertEquals(LOCK_VALUE, lockValue, "Redis 中锁 value 必须匹配持有者");
        assertNotNull(ttl, "TTL 不应为 null");
        assertTrue(ttl > 0 && ttl <= 10, "锁 TTL 必须在有效范围内");

        boolean second = simpleRedisLock.tryLock(LOCK_KEY, WRONG_VALUE, 10, TimeUnit.SECONDS);
        log.info("重复加锁结果={}", second);
        assertFalse(second, "重复加锁应失败");
        assertEquals(LOCK_VALUE, simpleRedisLockRedisTemplate.opsForValue().get(LOCK_KEY), "重复加锁失败后原 value 不应变化");
    }

    @Test
    public void testUnlockWithOwnerValueDeletesLock() {
        log.info("验证正确 lockValue 解锁会删除锁，lockKey={}", LOCK_KEY);
        assertTrue(simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "加锁应成功");
        boolean unlocked = simpleRedisLock.unlock(LOCK_KEY, LOCK_VALUE);
        boolean exists = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LOCK_KEY));
        log.info("正确解锁结果={}，解锁后 key 存在={}", unlocked, exists);
        assertTrue(unlocked, "正确 lockValue 解锁应返回 true");
        assertFalse(exists, "正确解锁后 key 应被删除");
    }

    @Test
    public void testUnlockWithWrongValueKeepsLock() {
        log.info("验证错误 lockValue 解锁不会删除锁，lockKey={}", LOCK_KEY);
        assertTrue(simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "加锁应成功");
        boolean unlocked = simpleRedisLock.unlock(LOCK_KEY, WRONG_VALUE);
        String lockValue = simpleRedisLockRedisTemplate.opsForValue().get(LOCK_KEY);
        log.info("错误解锁结果={}，Redis value 匹配原持有者={}", unlocked, LOCK_VALUE.equals(lockValue));
        assertFalse(unlocked, "错误 lockValue 解锁应返回 false");
        assertEquals(LOCK_VALUE, lockValue, "错误解锁后锁仍应存在");
    }

    @Test
    public void testLockExpirationAllowsRelock() throws Exception {
        String lockKey = "test:lock:expire";
        log.info("验证锁过期后可重新加锁，lockKey={}", lockKey);
        assertTrue(simpleRedisLock.tryLock(lockKey, LOCK_VALUE, 1, TimeUnit.SECONDS), "首次加锁应成功");
        Thread.sleep(1500L);
        boolean relocked = simpleRedisLock.tryLock(lockKey, WRONG_VALUE, 10, TimeUnit.SECONDS);
        String lockValue = simpleRedisLockRedisTemplate.opsForValue().get(lockKey);
        log.info("过期后重新加锁结果={}，Redis value 匹配新持有者={}", relocked, WRONG_VALUE.equals(lockValue));
        assertTrue(relocked, "锁过期后重新加锁应成功");
        assertEquals(WRONG_VALUE, lockValue, "重新加锁后 value 应更新为新持有者");
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
        boolean completed = completeLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        String lockValue = simpleRedisLockRedisTemplate.opsForValue().get(lockKey);
        log.info("并发加锁完成={}，成功次数={}，Redis value 存在={}", completed, successCount.get(), lockValue != null);
        assertTrue(completed, "并发测试线程必须在 10 秒内结束");
        assertEquals(1, successCount.get(), "同一个 lockKey 并发竞争时只能有一个持有者");
        assertNotNull(lockValue, "成功加锁后 Redis 中必须存在锁 value");
    }

    @Test
    public void testLeaseRenewKeepsLockBeyondInitialTtl() throws Exception {
        log.info("验证显式续租跨越初始 TTL 后仍保持互斥，lockKey={}", LEASE_RENEW_KEY);
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_RENEW_KEY, 400, TimeUnit.MILLISECONDS);
        log.info("首次获取续租租约结果={}", optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "首次获取租约应成功");
        RedisLockLease lease = optionalLease.get();
        Thread.sleep(200L);
        boolean renewed = lease.renew(800, TimeUnit.MILLISECONDS);
        Long pttl = simpleRedisLockRedisTemplate.getExpire(LEASE_RENEW_KEY, TimeUnit.MILLISECONDS);
        log.info("显式续租结果={}，续租后 PTTL={} 毫秒", renewed, pttl);
        assertTrue(renewed, "正确 owner 应能原子续租");
        assertNotNull(pttl, "续租后 PTTL 不应为 null");
        assertTrue(pttl > 300L, "续租后 PTTL 应覆盖原始租约截止点");

        Thread.sleep(300L);
        Optional<RedisLockLease> competitor = simpleRedisLock.tryLockWithLease(
                LEASE_RENEW_KEY, 1, TimeUnit.SECONDS);
        log.info("跨越初始 TTL 后竞争租约存在={}", competitor.isPresent());
        assertFalse(competitor.isPresent(), "显式续租后竞争者不应在原始 TTL 截止点取得锁");
        boolean released = lease.release();
        Optional<RedisLockLease> successor = simpleRedisLock.tryLockWithLease(
                LEASE_RENEW_KEY, 1, TimeUnit.SECONDS);
        log.info("释放结果={}，释放后竞争租约存在={}", released, successor.isPresent());
        assertTrue(released, "租约持有者应能释放锁");
        assertTrue(successor.isPresent(), "释放后竞争者应能立即取得锁");
        successor.get().close();
    }

    @Test
    public void testLeaseExpiresWithoutExplicitRenew() throws Exception {
        log.info("验证未显式续租的租约会自然过期，lockKey={}", LEASE_EXPIRE_KEY);
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_EXPIRE_KEY, 250, TimeUnit.MILLISECONDS);
        log.info("首次获取自然过期租约结果={}", optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "首次获取租约应成功");
        Thread.sleep(500L);
        Optional<RedisLockLease> successor = simpleRedisLock.tryLockWithLease(
                LEASE_EXPIRE_KEY, 1, TimeUnit.SECONDS);
        log.info("未续租后竞争租约存在={}", successor.isPresent());
        assertTrue(successor.isPresent(), "未调用 renew 时租约应自然到期，不得存在后台续租");
        successor.get().close();
        optionalLease.get().close();
    }

    @Test
    public void testStaleLeaseCannotRenewOrReleaseSuccessor() throws Exception {
        log.info("验证过期租约不能续租或释放新持有者，lockKey={}", LEASE_STALE_KEY);
        Optional<RedisLockLease> first = simpleRedisLock.tryLockWithLease(
                LEASE_STALE_KEY, 250, TimeUnit.MILLISECONDS);
        log.info("第一个租约获取结果={}", first.isPresent());
        assertTrue(first.isPresent(), "第一个租约应成功获取");
        Thread.sleep(500L);
        Optional<RedisLockLease> successor = simpleRedisLock.tryLockWithLease(
                LEASE_STALE_KEY, 1, TimeUnit.SECONDS);
        log.info("第一个租约过期后新持有者获取结果={}", successor.isPresent());
        assertTrue(successor.isPresent(), "第一个租约过期后新持有者应能获取锁");
        boolean renewed = first.get().renew(1, TimeUnit.SECONDS);
        boolean released = first.get().release();
        String successorValue = simpleRedisLockRedisTemplate.opsForValue().get(LEASE_STALE_KEY);
        Long successorPttl = simpleRedisLockRedisTemplate.getExpire(LEASE_STALE_KEY, TimeUnit.MILLISECONDS);
        log.info("旧租约续租结果={}，释放结果={}，新持有者 key 存在={}，PTTL={}",
                renewed, released, successorValue != null, successorPttl);
        assertFalse(renewed, "过期 owner 不得续租新持有者的锁");
        assertFalse(released, "过期 owner 不得释放新持有者的锁");
        assertNotNull(successorValue, "旧 owner 操作后新持有者的 key 必须保留");
        assertNotNull(successorPttl, "旧 owner 操作后新持有者的 TTL 必须保留");
        assertTrue(successorPttl > 0, "旧 owner 操作后新持有者的 TTL 必须有效");
        successor.get().close();
    }

    @Test
    public void testLeaseCompetitionDoesNotChangeCurrentOwnerTtl() {
        log.info("验证竞争租约失败不会影响当前 owner 或 TTL，lockKey={}", LEASE_COMPETITION_KEY);
        Optional<RedisLockLease> owner = simpleRedisLock.tryLockWithLease(
                LEASE_COMPETITION_KEY, 2, TimeUnit.SECONDS);
        log.info("当前 owner 获取租约结果={}", owner.isPresent());
        assertTrue(owner.isPresent(), "当前 owner 应成功获取租约");
        Long pttlBeforeCompetition = simpleRedisLockRedisTemplate.getExpire(
                LEASE_COMPETITION_KEY, TimeUnit.MILLISECONDS);
        Optional<RedisLockLease> competitor = simpleRedisLock.tryLockWithLease(
                LEASE_COMPETITION_KEY, 1, TimeUnit.SECONDS);
        String currentValue = simpleRedisLockRedisTemplate.opsForValue().get(LEASE_COMPETITION_KEY);
        Long pttlAfterCompetition = simpleRedisLockRedisTemplate.getExpire(
                LEASE_COMPETITION_KEY, TimeUnit.MILLISECONDS);
        log.info("竞争结果={}，当前 key 存在={}，竞争前 PTTL={}，竞争后 PTTL={}",
                competitor.isPresent(), currentValue != null, pttlBeforeCompetition, pttlAfterCompetition);
        assertFalse(competitor.isPresent(), "同一 key 已存在租约时竞争者必须返回空");
        assertNotNull(currentValue, "竞争失败后当前 owner 的 key 必须保留");
        assertNotNull(pttlBeforeCompetition, "竞争前当前 owner 的 PTTL 不应为 null");
        assertNotNull(pttlAfterCompetition, "竞争失败后当前 owner 的 PTTL 不应为 null");
        assertTrue(pttlAfterCompetition > 0L, "竞争失败后当前 owner 的 PTTL 必须有效");
        assertTrue(pttlAfterCompetition <= pttlBeforeCompetition, "竞争失败不得延长当前 owner 的租约");
        owner.get().close();
    }

    @Test
    public void testLeaseRejectsSubMillisecondDurationWithoutDeletingCurrentLock() {
        log.info("验证不足 1 毫秒的租约时长会被拒绝且不影响当前锁，lockKey={}", LEASE_INVALID_TIME_KEY);
        ValidationException acquireException = null;
        try {
            simpleRedisLock.tryLockWithLease(LEASE_INVALID_TIME_KEY, 1, TimeUnit.NANOSECONDS);
        } catch (ValidationException e) {
            acquireException = e;
        }
        boolean existsAfterInvalidAcquire = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_INVALID_TIME_KEY));
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_INVALID_TIME_KEY, 2, TimeUnit.SECONDS);
        log.info("无效获取异常码={}，异常消息={}，无效获取后 key 存在={}，有效获取结果={}",
                acquireException == null ? null : acquireException.getErrorCode(),
                acquireException == null ? null : acquireException.getMessage(), existsAfterInvalidAcquire, optionalLease.isPresent());
        assertNotNull(acquireException, "不足 1 毫秒的租约获取必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, acquireException.getErrorCode(),
                "不足 1 毫秒的租约获取必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, acquireException.getMessage(),
                "不足 1 毫秒的租约获取必须返回明确异常消息");
        assertFalse(existsAfterInvalidAcquire, "无效租约获取不得写入 Redis key");
        assertTrue(optionalLease.isPresent(), "有效租约时长应能正常获取锁");

        RedisLockLease lease = optionalLease.get();
        Long pttlBeforeInvalidRenew = simpleRedisLockRedisTemplate.getExpire(
                LEASE_INVALID_TIME_KEY, TimeUnit.MILLISECONDS);
        ValidationException renewException = null;
        try {
            lease.renew(1, TimeUnit.NANOSECONDS);
        } catch (ValidationException e) {
            renewException = e;
        }
        boolean existsAfterInvalidRenew = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_INVALID_TIME_KEY));
        Long pttlAfterInvalidRenew = simpleRedisLockRedisTemplate.getExpire(
                LEASE_INVALID_TIME_KEY, TimeUnit.MILLISECONDS);
        log.info("无效续租异常码={}，异常消息={}，无效续租后 key 存在={}，续租前 PTTL={}，续租后 PTTL={}",
                renewException == null ? null : renewException.getErrorCode(),
                renewException == null ? null : renewException.getMessage(), existsAfterInvalidRenew,
                pttlBeforeInvalidRenew, pttlAfterInvalidRenew);
        assertNotNull(renewException, "不足 1 毫秒的续租必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, renewException.getErrorCode(),
                "不足 1 毫秒的续租必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, renewException.getMessage(),
                "不足 1 毫秒的续租必须返回明确异常消息");
        assertTrue(existsAfterInvalidRenew, "无效续租不得删除当前 owner 的 Redis key");
        assertNotNull(pttlBeforeInvalidRenew, "无效续租前当前 owner 的 PTTL 不应为 null");
        assertNotNull(pttlAfterInvalidRenew, "无效续租后当前 owner 的 PTTL 不应为 null");
        assertTrue(pttlAfterInvalidRenew > 0L, "无效续租后当前 owner 的 PTTL 必须有效");
        assertTrue(pttlAfterInvalidRenew <= pttlBeforeInvalidRenew, "无效续租不得延长当前 owner 的 PTTL");
        lease.close();
    }

    @Test
    public void testLeaseRejectsNullTimeUnit() {
        log.info("验证租约时间单位为空会被拒绝，lockKey={}", LEASE_INVALID_TIME_KEY);
        ValidationException exception = null;
        try {
            simpleRedisLock.tryLockWithLease(LEASE_INVALID_TIME_KEY, 1, null);
        } catch (ValidationException e) {
            exception = e;
        }
        boolean existsAfterInvalidAcquire = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_INVALID_TIME_KEY));
        log.info("空时间单位异常码={}，异常消息={}，无效获取后 key 存在={}",
                exception == null ? null : exception.getErrorCode(),
                exception == null ? null : exception.getMessage(), existsAfterInvalidAcquire);
        assertNotNull(exception, "时间单位为空的租约获取必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_UNIT_REQUIRED, exception.getErrorCode(), "空时间单位必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_UNIT_REQUIRED, exception.getMessage(), "空时间单位必须返回明确异常消息");
        assertFalse(existsAfterInvalidAcquire, "空时间单位的租约获取不得写入 Redis key");

        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_INVALID_TIME_KEY, 2, TimeUnit.SECONDS);
        log.info("空时间单位续租前，有效租约获取结果={}", optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "有效租约时长应能正常获取锁");
        RedisLockLease lease = optionalLease.get();
        Long pttlBeforeInvalidRenew = simpleRedisLockRedisTemplate.getExpire(
                LEASE_INVALID_TIME_KEY, TimeUnit.MILLISECONDS);
        ValidationException renewException = null;
        try {
            lease.renew(1, null);
        } catch (ValidationException e) {
            renewException = e;
        }
        boolean existsAfterInvalidRenew = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_INVALID_TIME_KEY));
        Long pttlAfterInvalidRenew = simpleRedisLockRedisTemplate.getExpire(
                LEASE_INVALID_TIME_KEY, TimeUnit.MILLISECONDS);
        log.info("空时间单位续租异常码={}，异常消息={}，无效续租后 key 存在={}，续租前 PTTL={}，续租后 PTTL={}",
                renewException == null ? null : renewException.getErrorCode(),
                renewException == null ? null : renewException.getMessage(), existsAfterInvalidRenew,
                pttlBeforeInvalidRenew, pttlAfterInvalidRenew);
        assertNotNull(renewException, "时间单位为空的续租必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_UNIT_REQUIRED, renewException.getErrorCode(), "空时间单位续租必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_UNIT_REQUIRED, renewException.getMessage(), "空时间单位续租必须返回明确异常消息");
        assertTrue(existsAfterInvalidRenew, "空时间单位续租不得删除当前 owner 的 Redis key");
        assertNotNull(pttlBeforeInvalidRenew, "空时间单位续租前当前 owner 的 PTTL 不应为 null");
        assertNotNull(pttlAfterInvalidRenew, "空时间单位续租后当前 owner 的 PTTL 不应为 null");
        assertTrue(pttlAfterInvalidRenew > 0L, "空时间单位续租后当前 owner 的 PTTL 必须有效");
        assertTrue(pttlAfterInvalidRenew <= pttlBeforeInvalidRenew, "空时间单位续租不得延长当前 owner 的 PTTL");
        lease.close();
    }

    @Test
    public void testLeaseRejectsNonPositiveDurationWithoutWritingRedis() {
        log.info("验证零值和负数租约时长会被拒绝，lockKey={}", LEASE_INVALID_TIME_KEY);
        ValidationException zeroException = null;
        ValidationException negativeException = null;
        try {
            simpleRedisLock.tryLockWithLease(LEASE_INVALID_TIME_KEY, 0, TimeUnit.MILLISECONDS);
        } catch (ValidationException e) {
            zeroException = e;
        }
        try {
            simpleRedisLock.tryLockWithLease(LEASE_INVALID_TIME_KEY, -1, TimeUnit.SECONDS);
        } catch (ValidationException e) {
            negativeException = e;
        }
        boolean existsAfterInvalidAcquire = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_INVALID_TIME_KEY));
        log.info("零值异常码={}，异常消息={}，负数异常码={}，异常消息={}，无效获取后 key 存在={}",
                zeroException == null ? null : zeroException.getErrorCode(),
                zeroException == null ? null : zeroException.getMessage(),
                negativeException == null ? null : negativeException.getErrorCode(),
                negativeException == null ? null : negativeException.getMessage(), existsAfterInvalidAcquire);
        assertNotNull(zeroException, "零值租约时长必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, zeroException.getErrorCode(),
                "零值租约时长必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, zeroException.getMessage(),
                "零值租约时长必须返回明确异常消息");
        assertNotNull(negativeException, "负数租约时长必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, negativeException.getErrorCode(),
                "负数租约时长必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, negativeException.getMessage(),
                "负数租约时长必须返回明确异常消息");
        assertFalse(existsAfterInvalidAcquire, "零值和负数租约获取不得写入 Redis key");

        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_INVALID_TIME_KEY, 2, TimeUnit.SECONDS);
        log.info("零值和负数续租前，有效租约获取结果={}", optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "有效租约时长应能正常获取锁");
        RedisLockLease lease = optionalLease.get();
        Long pttlBeforeInvalidRenew = simpleRedisLockRedisTemplate.getExpire(
                LEASE_INVALID_TIME_KEY, TimeUnit.MILLISECONDS);
        ValidationException zeroRenewException = null;
        ValidationException negativeRenewException = null;
        try {
            lease.renew(0, TimeUnit.MILLISECONDS);
        } catch (ValidationException e) {
            zeroRenewException = e;
        }
        try {
            lease.renew(-1, TimeUnit.SECONDS);
        } catch (ValidationException e) {
            negativeRenewException = e;
        }
        boolean existsAfterInvalidRenew = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_INVALID_TIME_KEY));
        Long pttlAfterInvalidRenew = simpleRedisLockRedisTemplate.getExpire(
                LEASE_INVALID_TIME_KEY, TimeUnit.MILLISECONDS);
        log.info("零值续租异常码={}，异常消息={}，负数续租异常码={}，异常消息={}，无效续租后 key 存在={}，续租前 PTTL={}，续租后 PTTL={}",
                zeroRenewException == null ? null : zeroRenewException.getErrorCode(),
                zeroRenewException == null ? null : zeroRenewException.getMessage(),
                negativeRenewException == null ? null : negativeRenewException.getErrorCode(),
                negativeRenewException == null ? null : negativeRenewException.getMessage(),
                existsAfterInvalidRenew, pttlBeforeInvalidRenew, pttlAfterInvalidRenew);
        assertNotNull(zeroRenewException, "零值续租必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, zeroRenewException.getErrorCode(),
                "零值续租必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, zeroRenewException.getMessage(),
                "零值续租必须返回明确异常消息");
        assertNotNull(negativeRenewException, "负数续租必须被拒绝");
        assertEquals(ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, negativeRenewException.getErrorCode(),
                "负数续租必须返回明确错误码");
        assertEquals(ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND, negativeRenewException.getMessage(),
                "负数续租必须返回明确异常消息");
        assertTrue(existsAfterInvalidRenew, "零值和负数续租不得删除当前 owner 的 Redis key");
        assertNotNull(pttlBeforeInvalidRenew, "零值和负数续租前当前 owner 的 PTTL 不应为 null");
        assertNotNull(pttlAfterInvalidRenew, "零值和负数续租后当前 owner 的 PTTL 不应为 null");
        assertTrue(pttlAfterInvalidRenew > 0L, "零值和负数续租后当前 owner 的 PTTL 必须有效");
        assertTrue(pttlAfterInvalidRenew <= pttlBeforeInvalidRenew, "零值和负数续租不得延长当前 owner 的 PTTL");
        lease.close();
    }

    @Test
    public void testLeaseCloseReleasesLockForSuccessor() {
        log.info("验证首次调用 close 会释放租约，lockKey={}", LEASE_CLOSE_KEY);
        Optional<RedisLockLease> owner = simpleRedisLock.tryLockWithLease(
                LEASE_CLOSE_KEY, 1, TimeUnit.SECONDS);
        log.info("当前 owner 获取 close 租约结果={}", owner.isPresent());
        assertTrue(owner.isPresent(), "当前 owner 应成功获取租约");
        owner.get().close();
        boolean existsAfterClose = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_CLOSE_KEY));
        Optional<RedisLockLease> successor = simpleRedisLock.tryLockWithLease(
                LEASE_CLOSE_KEY, 1, TimeUnit.SECONDS);
        log.info("首次 close 后 key 存在={}，新 owner 获取结果={}", existsAfterClose, successor.isPresent());
        assertFalse(existsAfterClose, "首次 close 后 Redis key 应删除");
        assertTrue(successor.isPresent(), "首次 close 后新 owner 应能立即获取租约");
        successor.get().close();
    }

    @Test
    public void testLeaseReleaseAndCloseAreIdempotent() {
        log.info("验证租约 release 与 close 幂等，lockKey={}", LEASE_RELEASE_KEY);
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_RELEASE_KEY, 1, TimeUnit.SECONDS);
        log.info("首次获取幂等释放租约结果={}", optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "首次获取租约应成功");
        RedisLockLease lease = optionalLease.get();
        boolean firstRelease = lease.release();
        boolean secondRelease = lease.release();
        lease.close();
        boolean renewed = lease.renew(1, TimeUnit.SECONDS);
        boolean renewedWithInvalidDuration = lease.renew(0, TimeUnit.MILLISECONDS);
        boolean exists = Boolean.TRUE.equals(simpleRedisLockRedisTemplate.hasKey(LEASE_RELEASE_KEY));
        log.info("首次释放={}，重复释放={}，释放后有效续租={}，释放后无效续租={}，Redis key 存在={}",
                firstRelease, secondRelease, renewed, renewedWithInvalidDuration, exists);
        assertTrue(firstRelease, "首次 release 应释放当前 owner 的锁");
        assertFalse(secondRelease, "重复 release 不应重复执行 Redis 解锁");
        assertFalse(renewed, "已释放的租约不得再次续租");
        assertFalse(renewedWithInvalidDuration, "已释放的租约必须直接返回 false，不应再校验续租时长");
        assertFalse(exists, "release 后 Redis key 应删除");
    }
}
