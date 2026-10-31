package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

/** 产品级吊销导出包元数据；签发任务与 grant 签发实体独立。 */
@Entity
@Table(name = "license_revocation_package", indexes = @Index(
        name = "idx_license_revocation_package_scope", columnList = "issuer_id,product_id,sequence"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_license_revocation_package_operation", columnNames = "operation_id"),
                @UniqueConstraint(name = "uk_license_revocation_package_idempotency", columnNames = "idempotency_digest"),
                @UniqueConstraint(name = "uk_license_revocation_package_sequence",
                        columnNames = {"issuer_id", "product_id", "sequence"})})
public class RevocationPackageEntity {

    @Id
    @Column(name = "package_id", length = 128, nullable = false)
    private String packageId;
    @Column(name = "operation_id", length = 128, nullable = false)
    private String operationId;
    @Column(name = "issuer_id", length = 64, nullable = false)
    private String issuerId;
    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;
    @Column(name = "sequence", nullable = false)
    private long sequence;
    @Column(name = "snapshot_id", length = 128, nullable = false)
    private String snapshotId;
    @Column(name = "signing_profile_id", length = 128, nullable = false)
    private String signingProfileId;
    @Column(name = "kid", length = 128, nullable = false)
    private String kid;
    @Column(name = "key_version", nullable = false)
    private int keyVersion;
    @Column(name = "state", length = 16, nullable = false)
    private String state;
    @Column(name = "execution_generation", nullable = false)
    private long executionGeneration;
    @Lob
    @Column(name = "manifest_jws")
    private String manifestJws;
    @Column(name = "part_count", nullable = false)
    private int partCount;
    @Column(name = "manifest_sha256", length = 64, columnDefinition = "char(64)")
    private String manifestSha256;
    @Column(name = "reason_category", length = 64)
    private String reasonCategory;
    @Column(name = "idempotency_digest", length = 128, nullable = false,
            columnDefinition = "varchar(128) COLLATE utf8mb4_bin")
    private String idempotencyDigest;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RevocationPackageEntity() {
    }

    public RevocationPackageEntity(String packageId, String operationId, String issuerId, String productId,
                                   long sequence, String snapshotId, String signingProfileId, String kid,
                                   int keyVersion, String idempotencyDigest) {
        this.packageId = packageId;
        this.operationId = operationId;
        this.issuerId = issuerId;
        this.productId = productId;
        this.sequence = sequence;
        this.snapshotId = snapshotId;
        this.signingProfileId = signingProfileId;
        this.kid = kid;
        this.keyVersion = keyVersion;
        this.idempotencyDigest = idempotencyDigest;
        this.state = "PENDING";
        this.executionGeneration = 1L;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getPackageId() { return packageId; }
    public String getOperationId() { return operationId; }
    public String getIssuerId() { return issuerId; }
    public String getProductId() { return productId; }
    public long getSequence() { return sequence; }
    public String getSnapshotId() { return snapshotId; }
    public String getSigningProfileId() { return signingProfileId; }
    public String getKid() { return kid; }
    public int getKeyVersion() { return keyVersion; }
    public String getState() { return state; }
    public long getExecutionGeneration() { return executionGeneration; }
    public String getManifestJws() { return manifestJws; }
    public int getPartCount() { return partCount; }
    public String getManifestSha256() { return manifestSha256; }
    public String getReasonCategory() { return reasonCategory; }
    public String getIdempotencyDigest() { return idempotencyDigest; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markSigning() { state = "SIGNING"; updatedAt = Instant.now(); }
    public void markReady(String manifestJws, int partCount, String manifestSha256) {
        this.manifestJws = manifestJws;
        this.partCount = partCount;
        this.manifestSha256 = manifestSha256;
        this.state = "READY";
        this.updatedAt = Instant.now();
    }
    public void markFailed(String reason) { state = "FAILED"; reasonCategory = reason; updatedAt = Instant.now(); }
    public void bumpExecutionGeneration() { executionGeneration++; updatedAt = Instant.now(); }
}
