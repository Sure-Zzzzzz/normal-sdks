package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Timestamp;

/**
 * Outbox 内部领取候选
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public final class OutboxCandidate {
    /**
     * 候选记录主键
     */
    private final Long recordId;
    /**
     * 领取前的乐观锁版本号
     */
    private final Long version;
    /**
     * 候选可领取时间（待投递/重试用 available_at，租约到期用 lease_until）
     */
    private final Timestamp eligibleAt;
}
