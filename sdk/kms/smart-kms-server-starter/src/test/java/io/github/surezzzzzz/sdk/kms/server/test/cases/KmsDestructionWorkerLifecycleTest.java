package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.service.DestructionJobService;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.service.KmsDestructionWorkerLifecycle;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * KMS 销毁 worker 生命周期测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsDestructionWorkerLifecycleTest {

    /**
     * 构造合法的启用 worker 配置。
     *
     * @return 启用 worker 的配置
     */
    private static SmartKmsServerProperties enabledProperties() {
        SmartKmsServerProperties properties = new SmartKmsServerProperties();
        properties.getWorker().setEnable(Boolean.TRUE);
        properties.getWorker().setInstanceId("test-kms-worker");
        properties.getWorker().setScanIntervalMillis(Long.valueOf(100L));
        properties.getWorker().setLeaseSeconds(Long.valueOf(30L));
        properties.getWorker().setMaxConsecutiveFailures(Integer.valueOf(3));
        return properties;
    }

    /**
     * 验证停止后不再触发新扫描，且不主动变更已领取任务。
     */
    @Test
    void shouldStopNewScanWithoutChangingClaimedJob() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> scheduledAction = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(scheduledAction.capture(), anyLong());
        SmartKmsServerProperties properties = enabledProperties();
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        log.info("worker 首次扫描已执行，实例标识: {}", properties.getWorker().getInstanceId());
        assertTrue(lifecycle.isRunning(), "worker 启动后必须处于运行状态");
        verify(destructionJobService).processDueJobs(properties.getWorker().getInstanceId());

        lifecycle.stop();
        scheduledAction.getValue().run();
        log.info("worker 停止后已执行残留调度回调");
        assertFalse(lifecycle.isRunning(), "worker 停止后不得继续运行");
        verify(scheduledFuture).cancel(false);
        verify(destructionJobService, times(1)).processDueJobs(properties.getWorker().getInstanceId());
    }

    /**
     * 验证禁用 worker 时不创建任务处理和调度。
     */
    @Test
    void shouldNotStartWhenWorkerDisabled() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        SmartKmsServerProperties properties = new SmartKmsServerProperties();
        properties.getWorker().setEnable(Boolean.FALSE);
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        log.info("禁用 worker 的启动调用已完成");
        assertFalse(lifecycle.isRunning(), "禁用 worker 不得进入运行状态");
        verify(destructionJobService, never()).processDueJobs(any(String.class));
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong());
    }

    /**
     * 验证启用 worker 缺少实例标识时自动生成进程内标识。
     */
    @Test
    void shouldGenerateInstanceIdForMissingEnabledWorker() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), anyLong());
        SmartKmsServerProperties properties = enabledProperties();
        properties.getWorker().setInstanceId(null);
        ArgumentCaptor<String> instanceIdCaptor = ArgumentCaptor.forClass(String.class);
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        verify(destructionJobService).processDueJobs(instanceIdCaptor.capture());
        String instanceId = instanceIdCaptor.getValue();
        log.info("缺省 worker 实例标识已自动生成: {}", instanceId);
        assertNotNull(UUID.fromString(instanceId), "缺省实例标识必须是 UUID");
        assertTrue(lifecycle.isRunning(), "缺省实例标识不得阻止 worker 启动");
        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), anyLong());
        lifecycle.stop();
    }

    /**
     * 验证空白实例标识同样使用自动生成值。
     */
    @Test
    void shouldGenerateInstanceIdForBlankConfiguredValue() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), anyLong());
        SmartKmsServerProperties properties = enabledProperties();
        properties.getWorker().setInstanceId(" \t ");
        ArgumentCaptor<String> instanceIdCaptor = ArgumentCaptor.forClass(String.class);
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        verify(destructionJobService).processDueJobs(instanceIdCaptor.capture());
        String instanceId = instanceIdCaptor.getValue();
        log.info("空白 worker 实例标识已自动生成: {}", instanceId);
        assertNotNull(UUID.fromString(instanceId), "空白配置必须生成 UUID 实例标识");
        lifecycle.stop();
    }

    /**
     * 验证显式配置的实例标识原样传递。
     */
    @Test
    void shouldUseConfiguredWorkerInstanceIdUnchanged() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), anyLong());
        SmartKmsServerProperties properties = enabledProperties();
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        log.info("使用显式 worker 实例标识: {}", properties.getWorker().getInstanceId());
        verify(destructionJobService).processDueJobs(properties.getWorker().getInstanceId());
        lifecycle.stop();
    }

    /**
     * 验证自动生成的实例标识在同一生命周期对象内保持不变。
     */
    @Test
    void shouldReuseGeneratedInstanceIdForScheduledScanAndRestart() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> scheduledAction = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(scheduledAction.capture(), anyLong());
        SmartKmsServerProperties properties = enabledProperties();
        properties.getWorker().setInstanceId(null);
        ArgumentCaptor<String> instanceIdCaptor = ArgumentCaptor.forClass(String.class);
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        scheduledAction.getValue().run();
        lifecycle.stop();
        lifecycle.start();
        verify(destructionJobService, times(3)).processDueJobs(instanceIdCaptor.capture());
        List<String> instanceIds = instanceIdCaptor.getAllValues();
        log.info("worker 同生命周期扫描与重启实例标识: {}", instanceIds);
        assertEquals(instanceIds.get(0), instanceIds.get(1), "定时扫描必须复用首次生成的实例标识");
        assertEquals(instanceIds.get(0), instanceIds.get(2), "同一 lifecycle 重启必须复用首次生成的实例标识");
        assertNotNull(UUID.fromString(instanceIds.get(0)), "自动生成的实例标识必须是 UUID");
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), anyLong());
        lifecycle.stop();
    }

    /**
     * 验证非法 worker 运行参数仍在启动阶段快速失败。
     */
    @Test
    void shouldFailFastForInvalidWorkerRuntimeConfiguration() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        SmartKmsServerProperties properties = enabledProperties();
        properties.getWorker().setInstanceId(null);
        properties.getWorker().setScanIntervalMillis(Long.valueOf(0L));
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        log.info("验证非法 worker 扫描间隔配置边界");
        assertThrows(KmsValidationException.class, lifecycle::start, "非法扫描间隔必须在启动阶段失败");
        verify(destructionJobService, never()).processDueJobs(any(String.class));
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong());
    }

    /**
     * 验证单次任务处理故障只隔离到下次调度。
     */
    @Test
    void shouldIsolateWorkerProcessingFailure() {
        DestructionJobService destructionJobService = mock(DestructionJobService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> scheduledAction = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(scheduledAction.capture(), anyLong());
        SmartKmsServerProperties properties = enabledProperties();
        doThrow(new KmsValidationException()).doNothing().when(destructionJobService)
                .processDueJobs(properties.getWorker().getInstanceId());
        KmsDestructionWorkerLifecycle lifecycle = new KmsDestructionWorkerLifecycle(destructionJobService, properties,
                taskScheduler);

        lifecycle.start();
        assertDoesNotThrow(scheduledAction.getValue()::run, "单次处理故障不得终止后续调度");
        log.info("worker 首次故障后已完成下一次调度");
        verify(destructionJobService, times(2)).processDueJobs(properties.getWorker().getInstanceId());
        lifecycle.stop();
    }
}
