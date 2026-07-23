package io.github.surezzzzzz.sdk.kms.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

/**
 * 销毁 worker 健康事实。
 *
 * <p>该模型只表达调度可用性和延迟，不包含任务详情、密码材料或底层异常信息。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsDestructionWorkerHealth {

    /**
     * 当前实例是否能够领取到期销毁任务。
     */
    private final boolean claimable;
    /**
     * 最近一次成功扫描到期任务的时间。
     */
    private final Instant lastSuccessfulScanAt;
    /**
     * 连续扫描或执行失败次数。
     */
    private final int consecutiveFailureCount;
    /**
     * 最早逾期未完成任务的延迟；没有逾期任务时可为 {@code null}。
     */
    private final Duration oldestOverdueDelay;
}
