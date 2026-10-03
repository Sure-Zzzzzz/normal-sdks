package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 密钥版本销毁任务。任务不承载密码材料。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsDestructionJob {

    private final String ownerPrincipalId;
    private final String keyRef;
    private final int keyVersion;
    private final KmsDestructionJobState state;
    private final Instant dueAt;
    private final String claimToken;
    private final Instant claimUntil;
    private final int attemptCount;
    private final Instant completedAt;
}
