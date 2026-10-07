package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterTypedPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认类型化远程策略刷新管理器
 * <p>与 v1 刷新管理器同构但完全分离：只拉取 v2 快照、只写类型化存储；
 * 刷新失败保留 last-known-good，不被失败结果覆盖。快照代次以部署配置的
 * expectedPolicyEpoch 为准：服务端代次不符按拉取失败处理（不替换、不降级改写）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultSmartRedisLimiterTypedPolicyRefreshManager
        implements SmartRedisLimiterTypedPolicyRefreshManager, ApplicationListener<ApplicationReadyEvent> {

    private final SmartRedisLimiterProperties properties;
    private final SmartRedisLimiterManagementClient managementClient;
    private final SmartRedisLimiterTypedPolicySnapshotStore snapshotStore;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean readyHandled = new AtomicBoolean();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
    private volatile ScheduledFuture<?> scheduledFuture;

    /**
     * 构造默认类型化刷新管理器
     *
     * @param properties        限流器配置
     * @param managementClient  策略客户端（由 management client 传输件提供）
     * @param snapshotStore     类型化快照存储
     */
    public DefaultSmartRedisLimiterTypedPolicyRefreshManager(
            SmartRedisLimiterProperties properties,
            SmartRedisLimiterManagementClient managementClient,
            SmartRedisLimiterTypedPolicySnapshotStore snapshotStore) {
        this.properties = properties;
        this.managementClient = managementClient;
        this.snapshotStore = snapshotStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new TypedPolicyRefreshThreadFactory());
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
                ? 0L
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
        try {
            SmartRedisLimiterAcceptedTypedPolicySnapshot current = snapshotStore.getCurrent();
            SmartRedisLimiterTypedPolicyFetchResult fetchResult = managementClient.fetchTypedPolicy(
                    properties.getMe(), current == null ? null : current.getEtag());
            if (closed.get()) {
                return true;
            }
            if (fetchResult.isNotModified()) {
                return true;
            }
            SmartRedisLimiterTypedPolicySnapshot snapshot = fetchResult.getSnapshot();
            Long expectedEpoch = properties.getTyped().getExpectedPolicyEpoch();
            if (expectedEpoch == null || snapshot.getPolicyEpoch() != expectedEpoch) {
                throw new IllegalStateException("类型化快照代次与部署预期不符：server="
                        + snapshot.getPolicyEpoch() + ", expected=" + expectedEpoch);
            }
            if (!closed.get()) {
                snapshotStore.replace(new SmartRedisLimiterAcceptedTypedPolicySnapshot(
                        snapshot, fetchResult.getEtag(), Instant.now()));
            }
            lastFailure.set(null);
        } catch (Exception ex) {
            if (!closed.get()) {
                lastFailure.set(ex);
                log.warn("SmartRedisLimiter 类型化策略刷新失败，继续使用 last-known-good", ex);
            }
        } finally {
            refreshing.set(false);
        }
        return true;
    }

    /**
     * 获取最近一次刷新失败原因（无失败为 null）
     *
     * @return 最近失败原因
     */
    @Override
    public Throwable getLastFailure() {
        return lastFailure.get();
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

    /**
     * 类型化策略刷新线程工厂
     */
    private static final class TypedPolicyRefreshThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "smart-limiter-typed-policy-refresh-"
                    + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
