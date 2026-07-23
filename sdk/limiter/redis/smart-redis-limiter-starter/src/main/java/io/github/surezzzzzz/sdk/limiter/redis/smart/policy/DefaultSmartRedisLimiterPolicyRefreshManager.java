package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.SmartRedisLimiterPolicyClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.SmartRedisLimiterPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterPolicyRefreshState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认远程策略刷新管理器
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultSmartRedisLimiterPolicyRefreshManager
        implements SmartRedisLimiterPolicyRefreshManager, ApplicationListener<ApplicationReadyEvent> {

    private final SmartRedisLimiterProperties properties;
    private final SmartRedisLimiterPolicyClient policyClient;
    private final SmartRedisLimiterPolicySnapshotValidator snapshotValidator;
    private final SmartRedisLimiterPolicySnapshotStore snapshotStore;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean readyHandled = new AtomicBoolean();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<SmartRedisLimiterPolicyRefreshState> refreshState =
            new AtomicReference<>(SmartRedisLimiterPolicyRefreshState.builder().build());
    private volatile ScheduledFuture<?> scheduledFuture;

    /**
     * 构造默认刷新管理器
     *
     * @param properties        限流器配置
     * @param policyClient      远程策略客户端
     * @param snapshotValidator 快照校验器
     * @param snapshotStore     快照存储
     */
    public DefaultSmartRedisLimiterPolicyRefreshManager(
            SmartRedisLimiterProperties properties,
            SmartRedisLimiterPolicyClient policyClient,
            SmartRedisLimiterPolicySnapshotValidator snapshotValidator,
            SmartRedisLimiterPolicySnapshotStore snapshotStore) {
        this.properties = properties;
        this.policyClient = policyClient;
        this.snapshotValidator = snapshotValidator;
        this.snapshotStore = snapshotStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new PolicyRefreshThreadFactory());
    }

    /**
     * 应用就绪后只注册一次固定延迟刷新任务
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!readyHandled.compareAndSet(false, true) || closed.get()) {
            return;
        }
        long interval = properties.getRemotePolicy().getRefreshIntervalMillis();
        long initialDelay = Boolean.TRUE.equals(properties.getRemotePolicy().getInitialRefresh())
                ? SmartRedisLimiterStarterConstant.TIMEOUT_EXECUTOR_KEEP_ALIVE_MILLIS
                : interval;
        scheduledFuture = scheduler.scheduleWithFixedDelay(
                this::refresh,
                initialDelay,
                interval,
                TimeUnit.MILLISECONDS);
    }

    /**
     * 手工触发一次刷新
     *
     * @return 本次是否执行
     */
    @Override
    public boolean refresh() {
        if (closed.get() || !refreshing.compareAndSet(false, true)) {
            return false;
        }
        Instant attemptAt = Instant.now();
        try {
            SmartRedisLimiterAcceptedPolicySnapshot current = snapshotStore.getCurrent();
            SmartRedisLimiterPolicyFetchResult fetchResult = policyClient.fetch(
                    properties.getMe(), current == null ? null : current.getEtag());
            if (closed.get()) {
                return true;
            }
            if (fetchResult.isNotModified()) {
                if (current == null) {
                    throw new SmartRedisLimiterException(
                            io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode
                                    .POLICY_RESPONSE_INVALID,
                            ErrorMessage.POLICY_INITIAL_NOT_MODIFIED);
                }
                updateSuccessState(attemptAt, Instant.now(), current);
                return true;
            }
            SmartRedisLimiterAcceptedPolicySnapshot accepted = snapshotValidator.validate(
                    fetchResult.getSnapshot(),
                    fetchResult.getEtag(),
                    current,
                    Instant.now());
            if (!closed.get()) {
                snapshotStore.replace(accepted);
                updateSuccessState(attemptAt, Instant.now(), accepted);
            }
        } catch (Exception ex) {
            if (!closed.get()) {
                updateFailureState(attemptAt, ex);
                log.warn("SmartRedisLimiter 远程策略刷新失败，继续使用 last-known-good", ex);
            }
        } finally {
            refreshing.set(false);
        }
        return true;
    }

    /**
     * 获取当前刷新状态
     *
     * @return 当前刷新状态
     */
    @Override
    public SmartRedisLimiterPolicyRefreshState getRefreshState() {
        return refreshState.get();
    }

    /**
     * 关闭刷新任务与 SDK 自有线程
     */
    @PreDestroy
    public void destroy() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> future = scheduledFuture;
        if (future != null) {
            future.cancel(true);
        }
        scheduler.shutdownNow();
    }

    private void updateSuccessState(Instant attemptAt,
                                    Instant successAt,
                                    SmartRedisLimiterAcceptedPolicySnapshot accepted) {
        refreshState.set(SmartRedisLimiterPolicyRefreshState.builder()
                .lastAttemptAt(attemptAt)
                .lastSuccessAt(successAt)
                .acceptedRevision(accepted.getRevision())
                .acceptedEtag(accepted.getEtag())
                .lastAttemptSuccessful(true)
                .build());
    }

    private void updateFailureState(Instant attemptAt, Exception exception) {
        SmartRedisLimiterPolicyRefreshState previous = refreshState.get();
        String reason = exception instanceof SmartRedisLimiterException
                ? ((SmartRedisLimiterException) exception).getErrorCode()
                : exception.getClass().getSimpleName();
        refreshState.set(SmartRedisLimiterPolicyRefreshState.builder()
                .lastAttemptAt(attemptAt)
                .lastSuccessAt(previous.getLastSuccessAt())
                .acceptedRevision(previous.getAcceptedRevision())
                .acceptedEtag(previous.getAcceptedEtag())
                .lastAttemptSuccessful(false)
                .lastFailureReason(reason)
                .build());
    }

    /**
     * 远程策略刷新线程工厂
     */
    private static final class PolicyRefreshThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger(
                SmartRedisLimiterStarterConstant.THREAD_INDEX_INITIAL_VALUE);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(
                    runnable,
                    SmartRedisLimiterStarterConstant.REMOTE_POLICY_REFRESH_THREAD_NAME_PREFIX
                            + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
