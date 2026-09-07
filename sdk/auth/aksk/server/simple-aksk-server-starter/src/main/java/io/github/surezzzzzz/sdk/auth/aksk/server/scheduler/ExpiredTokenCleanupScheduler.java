package io.github.surezzzzzz.sdk.auth.aksk.server.scheduler;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 过期Token定时清理任务。
 * <p>
 * 多实例部署时通过 Redis 分布式锁互斥：抢到锁的实例执行清理，
 * 其余实例直接跳过本次调度；清理失败不影响下次调度重试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.auth.aksk.server.cleanup",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class ExpiredTokenCleanupScheduler {

    private final OAuth2AuthorizationRepository authorizationRepository;
    private final SimpleRedisLock simpleRedisLock;
    private final SimpleAkskServerProperties properties;

    /**
     * 定时清理过期授权记录。cron 默认每天凌晨2点，可经
     * {@code io.github.surezzzzzz.sdk.auth.aksk.server.cleanup.cron} 覆盖。
     */
    @Scheduled(cron = "${" + SimpleAkskServerConstant.CONFIG_PREFIX + ".cleanup.cron:"
            + SimpleAkskServerConstant.DEFAULT_CLEANUP_CRON + "}")
    public void cleanupExpiredTokens() {
        SimpleAkskServerProperties.CleanupConfig cleanup = properties.getCleanup();
        Optional<RedisLockLease> lease = simpleRedisLock.tryLockWithLease(
                SimpleAkskServerConstant.CLEANUP_LOCK_KEY,
                cleanup.getLockLeaseSeconds(), TimeUnit.SECONDS);
        if (!lease.isPresent()) {
            log.info("Expired token cleanup skipped: lock held by another instance");
            return;
        }
        try {
            int deleted = authorizationRepository.deleteExpired();
            log.info("Scheduled expired token cleanup finished: {} authorizations deleted", deleted);
        } finally {
            lease.get().close();
        }
    }
}
