package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.scheduler.ExpiredTokenCleanupScheduler;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 过期 Token 定时清理任务单元测试。
 * <p>
 * 覆盖：锁分支（拿到锁执行清理并释放 / 抢不到锁跳过不触仓库）与
 * 分批删除循环（跨批累计删除、不足一批即停）。
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class ExpiredTokenCleanupSchedulerTest {

    @Mock
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;
    @Mock
    private SimpleRedisLock simpleRedisLock;

    private SimpleAkskServerProperties properties;
    private OAuth2AuthorizationRepository authorizationRepository;
    private ExpiredTokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new SimpleAkskServerProperties();
        properties.getCleanup().setBatchSize(2);
        authorizationRepository = new OAuth2AuthorizationRepository(
                authorizationEntityRepository, null, properties);
        scheduler = new ExpiredTokenCleanupScheduler(authorizationRepository, simpleRedisLock, properties);
    }

    private RedisLockLease lockedLease() {
        RedisLockLease lease = mock(RedisLockLease.class);
        when(simpleRedisLock.tryLockWithLease(
                eq(SimpleAkskServerConstant.CLEANUP_LOCK_KEY), anyLong(), any(TimeUnit.class)))
                .thenReturn(Optional.of(lease));
        return lease;
    }

    @Test
    void testAcquiresLockRunsBatchedCleanupAndReleases() {
        RedisLockLease lease = lockedLease();
        when(authorizationEntityRepository.deleteExpiredBatch(any(), eq(2)))
                .thenReturn(2, 2, 1);

        scheduler.cleanupExpiredTokens();

        verify(authorizationEntityRepository, times(3)).deleteExpiredBatch(any(), eq(2));
        verify(lease).close();
        log.info("✓ 拿到锁：分批删完全部过期记录（2+2+1=3 批）并释放锁");
    }

    @Test
    void testNothingExpiredRunsSingleBatchAndReleases() {
        RedisLockLease lease = lockedLease();
        when(authorizationEntityRepository.deleteExpiredBatch(any(), eq(2)))
                .thenReturn(0);

        scheduler.cleanupExpiredTokens();

        verify(authorizationEntityRepository, times(1)).deleteExpiredBatch(any(), eq(2));
        verify(lease).close();
        log.info("✓ 无过期记录：只跑一批即停并释放锁");
    }

    @Test
    void testLockUnavailableSkipsCleanupWithoutTouchingRepository() {
        when(simpleRedisLock.tryLockWithLease(
                eq(SimpleAkskServerConstant.CLEANUP_LOCK_KEY), anyLong(), any(TimeUnit.class)))
                .thenReturn(Optional.empty());

        scheduler.cleanupExpiredTokens();

        verify(authorizationEntityRepository, never()).deleteExpiredBatch(any(), anyInt());
        log.info("✓ 抢不到锁：直接跳过本次调度，不碰仓库");
    }

    @Test
    void testLeaseUsesConfiguredLockLeaseSeconds() {
        properties.getCleanup().setLockLeaseSeconds(42);
        RedisLockLease lease = lockedLease();
        when(authorizationEntityRepository.deleteExpiredBatch(any(), eq(2)))
                .thenReturn(0);

        scheduler.cleanupExpiredTokens();

        verify(simpleRedisLock).tryLockWithLease(
                eq(SimpleAkskServerConstant.CLEANUP_LOCK_KEY), eq(42L), eq(TimeUnit.SECONDS));
        verify(lease).close();
        log.info("✓ 锁租约时长取 cleanup.lock-lease-seconds 配置");
    }

    @Test
    void testCleanupFailureStillReleasesLease() {
        RedisLockLease lease = lockedLease();
        when(authorizationEntityRepository.deleteExpiredBatch(any(), eq(2)))
                .thenThrow(new RuntimeException("delete failed"));

        assertThrows(RuntimeException.class, () -> scheduler.cleanupExpiredTokens());

        verify(lease).close();
        log.info("✓ 清理中途抛异常：锁仍在 finally 释放，异常上抛交由下次调度重试");
    }

    @Test
    void testCleanupConfigDefaults() {
        SimpleAkskServerProperties.CleanupConfig defaults = new SimpleAkskServerProperties().getCleanup();

        assertEquals(Boolean.TRUE, defaults.getEnable());
        assertEquals(SimpleAkskServerConstant.DEFAULT_CLEANUP_CRON, defaults.getCron());
        assertEquals(SimpleAkskServerConstant.DEFAULT_CLEANUP_BATCH_SIZE, defaults.getBatchSize());
        assertEquals(SimpleAkskServerConstant.DEFAULT_CLEANUP_LOCK_LEASE_SECONDS, defaults.getLockLeaseSeconds());
        log.info("✓ CleanupConfig 四项默认值与常量一致（公开契约锚定）");
    }

    @Test
    void testDeleteExpiredReturnsTotalAcrossBatches() {
        when(authorizationEntityRepository.deleteExpiredBatch(any(), eq(2)))
                .thenReturn(2, 2, 1);

        int total = authorizationRepository.deleteExpired();

        assertEquals(5, total);
        verify(authorizationEntityRepository, times(3)).deleteExpiredBatch(any(), eq(2));
        log.info("✓ deleteExpired 跨批累计返回总删除数（2+2+1=5）");
    }
}
