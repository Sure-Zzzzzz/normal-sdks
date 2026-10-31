package io.github.surezzzzzz.sdk.license.server.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * 吊销包事务提交后的终态事件；幂等回读不重新发布。
 *
 * <p>只承载包、操作、产品和序号等协调标识，不携带签名、吊销明细或密钥资料。
 * 不从后台签名任务推断请求人员身份。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class RevocationPackageCompleted {

    private final String eventId = "lae-" + UUID.randomUUID();
    private final int eventVersion = LicenseAuditEvents.EVENT_VERSION;
    private final Instant occurredAt = Instant.now();
    private final String packageId;
    private final String operationId;
    private final String productId;
    private final long sequence;
    private final String state;
    private final String reasonCategory;

    public RevocationPackageCompleted(String packageId, String operationId, String productId,
                                      long sequence, String state, String reasonCategory) {
        this.packageId = packageId;
        this.operationId = operationId;
        this.productId = productId;
        this.sequence = sequence;
        this.state = state;
        this.reasonCategory = reasonCategory;
    }
}
