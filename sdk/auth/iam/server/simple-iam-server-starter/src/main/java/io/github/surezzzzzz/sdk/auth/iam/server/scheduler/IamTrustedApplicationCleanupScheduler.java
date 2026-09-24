package io.github.surezzzzzz.sdk.auth.iam.server.scheduler;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationCleanupWorker;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 可信应用异步删除调度器。
 *
 * <p>多实例仅允许一个节点领取操作；操作状态和批次游标仍落库，因此节点异常后可由下次
 * 调度接手，FAILED 则必须由管理员显式重试。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamTrustedApplicationCleanupScheduler {

    private final IamTrustedApplicationCleanupWorker cleanupWorker;
    private final SimpleRedisLock simpleRedisLock;

    /**
     * 定期领取一个待删应用，避免管理端请求线程参与长时间授权清理。
     */
    @Scheduled(fixedDelay = SimpleIamServerConstant.TRUSTED_APPLICATION_CLEANUP_SCHEDULE_DELAY_MILLIS)
    public void processNextOperation() {
        Optional<RedisLockLease> lease = simpleRedisLock.tryLockWithLease(
                SimpleIamServerConstant.TRUSTED_APPLICATION_CLEANUP_LOCK_KEY,
                SimpleIamServerConstant.TRUSTED_APPLICATION_CLEANUP_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        if (!lease.isPresent()) {
            return;
        }
        try {
            cleanupWorker.processNextOperation();
        } catch (RuntimeException exception) {
            log.error("可信应用异步删除调度执行失败", exception);
        } finally {
            lease.get().close();
        }
    }
}
