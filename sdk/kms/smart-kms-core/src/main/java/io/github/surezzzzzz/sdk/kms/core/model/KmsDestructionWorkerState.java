package io.github.surezzzzzz.sdk.kms.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 销毁 worker 持久化运行状态。
 *
 * <p>该模型保存 worker 扫描事实以支持故障恢复和健康判定，不包含销毁任务详情、密码材料或异常链。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsDestructionWorkerState {

    /**
     * worker 实例的稳定标识。
     */
    private final String instanceId;
    /**
     * 最近一次成功扫描到期任务的时间。
     */
    private final Instant lastSuccessfulScanAt;
    /**
     * 最近一次扫描或执行失败的时间。
     */
    private final Instant lastFailureAt;
    /**
     * 自最近一次成功扫描以来的连续失败次数。
     */
    private final int consecutiveFailureCount;
}
