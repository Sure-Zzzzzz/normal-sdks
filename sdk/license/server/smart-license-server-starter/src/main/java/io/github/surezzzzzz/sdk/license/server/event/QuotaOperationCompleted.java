package io.github.surezzzzzz.sdk.license.server.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * 已提交额度操作的最小观察事件；幂等重放不表示发生了新的扣减。
 *
 * <p>只包含协调标识、状态与持久围栏；不含数量、权益、认证材料或请求正文。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotaOperationCompleted {

    /** 观察类型区分创建重放、释放与续期，不补造新的额度变化事实。 */
    public enum Operation {
        RESERVATION_OBSERVED, RELEASE_OBSERVED, RENEWAL_OBSERVED
    }

    private final String eventId = "lae-" + UUID.randomUUID();
    private final int eventVersion = LicenseAuditEvents.EVENT_VERSION;
    private final Instant occurredAt = Instant.now();
    private final String principalId;
    private final String requestId;
    private final String grantId;
    private final String reservationId;
    private final String kind;
    private final String state;
    private final long fence;
    private final Operation operation;

    public QuotaOperationCompleted(String principalId, String requestId, String grantId, String reservationId,
                                    String kind, String state, long fence, Operation operation) {
        this.principalId = principalId;
        this.requestId = requestId;
        this.grantId = grantId;
        this.reservationId = reservationId;
        this.kind = kind;
        this.state = state;
        this.fence = fence;
        this.operation = operation;
    }
}
