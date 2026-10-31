package io.github.surezzzzzz.sdk.audit.license.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * License 业务审计记录（2.0 新领域字段族）
 *
 * <p>由 License 领域事件投影生成；不得携带 Compact JWS、Claims、签名、KMS keyRef、公钥材料或权益明文。
 * 旧 1.0 的 tenantId/licenseId/kmsKeyVersion 字段族随领域重构废弃，以 grantId/credentialId/kid 对位。</p>
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseAuditRecord {

    private String eventType;
    private String eventId;
    private Integer eventVersion;
    private Long timestamp;
    private String traceId;
    private String principalId;
    private String requestId;
    // ===== 授权域 =====
    private String grantId;
    private String customerId;
    private String productId;
    private String subjectKind;
    private String mode;
    private String modelVersionId;
    private String fromState;
    private String toState;
    // ===== 凭据域 =====
    private String credentialId;
    private String kid;
    // ===== 吊销域 =====
    private String revocationId;
    private String targetKind;
    private String targetId;
    private String reasonCategory;
    // ===== 吊销包域；不记录签名载荷、分片和吊销目标明细 =====
    private String packageId;
    private String operationId;
    private Long packageSequence;
    // ===== 模型域 =====
    private Long modelVersion;
    // ===== 角色协作域；无规则、目录名单或人员认证材料 =====
    private String projectionId;
    private String commandId;
    private Long desiredRevision;
    private String executionMode;
    // ===== 额度域；不记录数量、权益或业务操作正文 =====
    private String reservationId;
    private String quotaKind;
    private Long reservationFence;
    private String quotaOperation;
}
