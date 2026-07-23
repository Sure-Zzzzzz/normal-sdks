package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 密钥版本销毁任务。
 *
 * <p>任务仅保存可审计的资源标识、调度和领取状态，绝不承载私钥、对称密钥或其他密码材料。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsDestructionJob {

    /**
     * 待销毁版本所属 tenant。
     */
    private final String tenantId;
    /**
     * 待销毁版本所属逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 待销毁的精确版本号。
     */
    private final int keyVersion;
    /**
     * 当前销毁任务状态。
     */
    private final KmsDestructionJobState state;
    /**
     * 任务可执行销毁的最早时间。
     */
    private final Instant dueAt;
    /**
     * worker 领取任务后写入的随机领取令牌。
     */
    private final String claimToken;
    /**
     * 当前领取租约到期时间，过期后任务可被重新领取。
     */
    private final Instant claimUntil;
    /**
     * 已尝试执行销毁的次数。
     */
    private final int attemptCount;
    /**
     * 成功销毁并完成任务的时间。
     */
    private final Instant completedAt;
}
