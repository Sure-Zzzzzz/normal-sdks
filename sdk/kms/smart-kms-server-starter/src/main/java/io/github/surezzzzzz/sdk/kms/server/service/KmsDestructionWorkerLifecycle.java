package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.service.DestructionJobService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

/**
 * KMS 内部销毁 worker 生命周期管理器。
 *
 * @author surezzzzzz
 */
public class KmsDestructionWorkerLifecycle implements SmartLifecycle {

    private final DestructionJobService destructionJobService;
    private final SmartKmsServerProperties properties;
    private final TaskScheduler taskScheduler;
    private volatile boolean running;
    private volatile String resolvedInstanceId;
    private ScheduledFuture<?> scheduledFuture;

    /**
     * 创建内部销毁 worker 生命周期管理器。
     */
    public KmsDestructionWorkerLifecycle(DestructionJobService destructionJobService,
                                         SmartKmsServerProperties properties, TaskScheduler taskScheduler) {
        this.destructionJobService = destructionJobService;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    /**
     * 创建单线程内部 worker 调度器。
     *
     * @return KMS 内部 worker 调度器
     */
    public static TaskScheduler createTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("smart-kms-destruction-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 启动后立即恢复扫描，并按固定间隔继续扫描。
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (properties.getWorker() != null && !Boolean.TRUE.equals(properties.getWorker().getEnable())) {
            return;
        }
        validateWorkerConfiguration();
        resolveInstanceId();
        running = true;
        runSafely();
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                runSafely();
            }
        }, properties.getWorker().getScanIntervalMillis().longValue());
    }

    /**
     * 关闭时立即停止新扫描；不主动续租、释放、取消或完成任务。
     */
    @Override
    public synchronized void stop() {
        running = false;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    /**
     * 生命周期回调停止 worker。
     */
    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * 查询 worker 是否仍在运行。
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 将 worker 放在容器停止前尽早关闭。
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * worker 作为自动启动的内部基础设施。
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * 校验 worker 启动所需的正数运行参数。
     */
    private void validateWorkerConfiguration() {
        if (properties.getWorker() == null || properties.getWorker().getScanIntervalMillis() == null
                || properties.getWorker().getScanIntervalMillis().longValue() < 1L
                || properties.getWorker().getLeaseSeconds() == null
                || properties.getWorker().getLeaseSeconds().longValue() < 1L
                || properties.getWorker().getMaxConsecutiveFailures() == null
                || properties.getWorker().getMaxConsecutiveFailures().intValue() < 1) {
            throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
        }
    }

    /**
     * 解析当前生命周期内固定的 worker 实例标识。
     */
    private void resolveInstanceId() {
        if (resolvedInstanceId != null) {
            return;
        }
        String configuredInstanceId = properties.getWorker().getInstanceId();
        String instanceId = StringUtils.hasText(configuredInstanceId) ? configuredInstanceId : UUID.randomUUID().toString();
        KmsValidationHelper.requireText(instanceId, SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH);
        resolvedInstanceId = instanceId;
    }

    /**
     * 将任务处理异常隔离给下次扫描与持久化失败计数。
     */
    private void runSafely() {
        if (!running) {
            return;
        }
        try {
            destructionJobService.processDueJobs(resolvedInstanceId);
        } catch (RuntimeException exception) {
            // 失败事实已由销毁任务服务持久化，下一次扫描按阈值决定是否继续领取。
        }
    }
}
