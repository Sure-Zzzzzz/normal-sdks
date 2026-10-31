package io.github.surezzzzzz.sdk.license.server.event;

import java.time.Instant;
import java.util.UUID;

/**
 * License 业务审计事件族（2.0 新领域）：服务端在业务事实成立点发布，
 * sdk/audit/license 监听器消费投影为审计记录。
 *
 * <p>事件为纯 POJO（Spring 事件载荷）；统一携带 eventId/eventVersion/occurredAt/
 * principalId/requestId（发布点从请求上下文直取）。载荷红线：不得携带 Compact JWS、
 * Claims、签名原文、KMS keyRef、公钥材料或权益明文。</p>
 *
 * @author surezzzzzz
 */
public final class LicenseAuditEvents {

    /** 事件形态版本（载荷结构演进时递增）。 */
    public static final int EVENT_VERSION = 1;

    private LicenseAuditEvents() {
    }

    private static String newEventId() {
        return "lae-" + UUID.randomUUID();
    }

    /** 客户授权创建。 */
    public static final class GrantCreated {

        public final String eventId;
        public final int eventVersion;
        public final Instant occurredAt;
        public final String principalId;
        public final String requestId;
        public final String grantId;
        public final String customerId;
        public final String productId;
        public final String subjectKind;
        public final String mode;
        public final String modelVersionId;

        public GrantCreated(String principalId, String requestId, String grantId, String customerId,
                            String productId, String subjectKind, String mode, String modelVersionId) {
            this.eventId = newEventId();
            this.eventVersion = EVENT_VERSION;
            this.occurredAt = Instant.now();
            this.principalId = principalId;
            this.requestId = requestId;
            this.grantId = grantId;
            this.customerId = customerId;
            this.productId = productId;
            this.subjectKind = subjectKind;
            this.mode = mode;
            this.modelVersionId = modelVersionId;
        }
    }

    /** 授权管理状态变更（启用/暂停；终态经吊销事件承载）。 */
    public static final class GrantStateChanged {

        public final String eventId;
        public final int eventVersion;
        public final Instant occurredAt;
        public final String principalId;
        public final String requestId;
        public final String grantId;
        public final String fromState;
        public final String toState;

        public GrantStateChanged(String principalId, String requestId, String grantId,
                                 String fromState, String toState) {
            this.eventId = newEventId();
            this.eventVersion = EVENT_VERSION;
            this.occurredAt = Instant.now();
            this.principalId = principalId;
            this.requestId = requestId;
            this.grantId = grantId;
            this.fromState = fromState;
            this.toState = toState;
        }
    }

    /** 离线凭据签发就绪（READY 成立点；不含签名原文）。 */
    public static final class CredentialIssued {

        public final String eventId;
        public final int eventVersion;
        public final Instant occurredAt;
        public final String principalId;
        public final String requestId;
        public final String grantId;
        public final String credentialId;
        public final String kid;

        public CredentialIssued(String principalId, String requestId, String grantId,
                                String credentialId, String kid) {
            this.eventId = newEventId();
            this.eventVersion = EVENT_VERSION;
            this.occurredAt = Instant.now();
            this.principalId = principalId;
            this.requestId = requestId;
            this.grantId = grantId;
            this.credentialId = credentialId;
            this.kid = kid;
        }
    }

    /** 吊销创建（不可撤回事实）。 */
    public static final class RevocationCreated {

        public final String eventId;
        public final int eventVersion;
        public final Instant occurredAt;
        public final String principalId;
        public final String requestId;
        public final String revocationId;
        public final String targetKind;
        public final String targetId;
        public final String reasonCategory;

        public RevocationCreated(String principalId, String requestId, String revocationId,
                                 String targetKind, String targetId, String reasonCategory) {
            this.eventId = newEventId();
            this.eventVersion = EVENT_VERSION;
            this.occurredAt = Instant.now();
            this.principalId = principalId;
            this.requestId = requestId;
            this.revocationId = revocationId;
            this.targetKind = targetKind;
            this.targetId = targetId;
            this.reasonCategory = reasonCategory;
        }
    }

    /** 条件模型发布（快照固化点）。 */
    public static final class ModelPublished {

        public final String eventId;
        public final int eventVersion;
        public final Instant occurredAt;
        public final String principalId;
        public final String requestId;
        public final String modelVersionId;
        public final String productId;
        public final long modelVersion;

        public ModelPublished(String principalId, String requestId, String modelVersionId,
                              String productId, long modelVersion) {
            this.eventId = newEventId();
            this.eventVersion = EVENT_VERSION;
            this.occurredAt = Instant.now();
            this.principalId = principalId;
            this.requestId = requestId;
            this.modelVersionId = modelVersionId;
            this.productId = productId;
            this.modelVersion = modelVersion;
        }
    }
}
