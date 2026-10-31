package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

/**
 * 不可撤回吊销记录（license_revocation）：凭据/授权/激活/固定资格/签名密钥的终态否定。
 *
 * <p>吊销只增不减（写入后不可撤销）；在线端每次判定复查即时生效，
 * 离线端经吊销包导出分发（包签名属后续批次）。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_revocation", uniqueConstraints =
        @UniqueConstraint(name = "uk_license_revocation_target", columnNames = {"target_kind", "target_id"}))
public class RevocationEntity {

    @Id
    @Column(name = "revocation_id", length = 64, nullable = false)
    private String revocationId;

    /** CREDENTIAL/GRANT/ACTIVATION/ASSIGNMENT/SIGNING_KEY。 */
    @Column(name = "target_kind", length = 32, nullable = false)
    private String targetKind;

    @Column(name = "target_id", length = 128, nullable = false)
    private String targetId;

    /** 原因分类（REVOKED_BY_OWNER/SECURITY_INCIDENT 等，不携带理由正文）。 */
    @Column(name = "reason_category", length = 64, nullable = false)
    private String reasonCategory;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    /** 操作者主体（principalId，审计关联）。 */
    @Column(name = "revoked_by", length = 128, nullable = false)
    private String revokedBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public RevocationEntity() {
    }

    public RevocationEntity(String revocationId, String targetKind, String targetId,
                            String reasonCategory, String revokedBy) {
        this.revocationId = revocationId;
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.reasonCategory = reasonCategory;
        this.revokedAt = Instant.now();
        this.revokedBy = revokedBy;
    }

    public String getRevocationId() {
        return revocationId;
    }

    public String getTargetKind() {
        return targetKind;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
