package io.github.surezzzzzz.sdk.auth.iam.server.scheduler;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamExpiredTokenCleanupService;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 过期 Token 定时清理任务。
 *
 * <p>多实例部署（IAM 双实例）通过 Redis 分布式锁互斥：抢到锁的实例执行清理，
 * 其余实例直接跳过本次调度；清理失败不影响下次调度重试。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = SimpleIamServerConstant.CONFIG_PREFIX + ".cleanup",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class IamExpiredTokenCleanupScheduler {

    private final IamExpiredTokenCleanupService cleanupService;
    private final SimpleRedisLock simpleRedisLock;
    private final SimpleIamServerProperties properties;

    /**
     * 定时清理过期 token 数据（refresh 族 + 授权行）。cron 默认每天凌晨 2 点，可经
     * {@code io.github.surezzzzzz.sdk.auth.iam.server.cleanup.cron} 覆盖。
     */
    @Scheduled(cron = "${" + SimpleIamServerConstant.CONFIG_PREFIX + ".cleanup.cron:"
            + SimpleIamServerConstant.DEFAULT_CLEANUP_CRON + "}")
    public void cleanupExpiredTokens() {
        Optional<RedisLockLease> lease = simpleRedisLock.tryLockWithLease(
                SimpleIamServerConstant.CLEANUP_LOCK_KEY,
                properties.getCleanup().getLockLeaseSeconds(), TimeUnit.SECONDS);
        if (!lease.isPresent()) {
            log.info("Expired token cleanup skipped: lock held by another instance");
            return;
        }
        try {
            cleanupService.cleanupExpired();
        } finally {
            lease.get().close();
        }
    }
}
