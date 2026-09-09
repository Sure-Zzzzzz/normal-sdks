package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.scheduler.IamExpiredTokenCleanupScheduler;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamExpiredTokenCleanupService;
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
 *
 * <p>覆盖：锁分支（拿到锁执行清理并释放 / 抢不到锁跳过不触清理服务 /
 * 清理抛异常仍释放）与锁租约配置接线；清理服务本身的分批删除逻辑
 * 由真实 MySQL 集成测试（{@link IamExpiredTokenCleanupIntegrationTest}）覆盖。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class IamExpiredTokenCleanupSchedulerTest {

    @Mock
    private IamExpiredTokenCleanupService cleanupService;
    @Mock
    private SimpleRedisLock simpleRedisLock;

    private SimpleIamServerProperties properties;
    private IamExpiredTokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new SimpleIamServerProperties();
        scheduler = new IamExpiredTokenCleanupScheduler(cleanupService, simpleRedisLock, properties);
    }

    private RedisLockLease lockedLease() {
        RedisLockLease lease = mock(RedisLockLease.class);
        when(simpleRedisLock.tryLockWithLease(
                eq(SimpleIamServerConstant.CLEANUP_LOCK_KEY), anyLong(), any(TimeUnit.class)))
                .thenReturn(Optional.of(lease));
        return lease;
    }

    @Test
    void testAcquiresLockRunsCleanupAndReleases() {
        RedisLockLease lease = lockedLease();

        scheduler.cleanupExpiredTokens();

        verify(cleanupService).cleanupExpired();
        verify(lease).close();
        log.info("✓ 拿到锁：执行清理并释放锁");
    }

    @Test
    void testLockUnavailableSkipsCleanupWithoutTouchingService() {
        when(simpleRedisLock.tryLockWithLease(
                eq(SimpleIamServerConstant.CLEANUP_LOCK_KEY), anyLong(), any(TimeUnit.class)))
                .thenReturn(Optional.empty());

        scheduler.cleanupExpiredTokens();

        verify(cleanupService, never()).cleanupExpired();
        log.info("✓ 抢不到锁：直接跳过本次调度，不碰清理服务");
    }

    @Test
    void testLeaseUsesConfiguredLockLeaseSeconds() {
        properties.getCleanup().setLockLeaseSeconds(42);
        RedisLockLease lease = lockedLease();

        scheduler.cleanupExpiredTokens();

        verify(simpleRedisLock).tryLockWithLease(
                eq(SimpleIamServerConstant.CLEANUP_LOCK_KEY), eq(42L), eq(TimeUnit.SECONDS));
        verify(lease).close();
        log.info("✓ 锁租约时长取 cleanup.lock-lease-seconds 配置");
    }

    @Test
    void testCleanupFailureStillReleasesLease() {
        RedisLockLease lease = lockedLease();
        when(cleanupService.cleanupExpired()).thenThrow(new RuntimeException("cleanup failed"));

        assertThrows(RuntimeException.class, () -> scheduler.cleanupExpiredTokens());

        verify(lease).close();
        log.info("✓ 清理中途抛异常：锁仍在 finally 释放，异常上抛交由下次调度重试");
    }

    @Test
    void testCleanupConfigDefaults() {
        SimpleIamServerProperties.CleanupConfig defaults = new SimpleIamServerProperties().getCleanup();

        assertEquals(Boolean.TRUE, defaults.getEnable());
        assertEquals(SimpleIamServerConstant.DEFAULT_CLEANUP_CRON, defaults.getCron());
        assertEquals(SimpleIamServerConstant.DEFAULT_CLEANUP_BATCH_SIZE, defaults.getBatchSize());
        assertEquals(SimpleIamServerConstant.DEFAULT_CLEANUP_LOCK_LEASE_SECONDS,
                defaults.getLockLeaseSeconds());
        log.info("✓ CleanupConfig 四项默认值与常量一致（公开契约锚定）");
    }
}
