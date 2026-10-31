package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

/** 产品吊销导出范围水位：序号分配和信任版本均按 issuer/product 隔离。 */
@Entity
@Table(name = "license_revocation_scope", uniqueConstraints = @UniqueConstraint(
        name = "uk_license_revocation_scope_product", columnNames = {"issuer_id", "product_id"}))
public class RevocationScopeEntity {

    private static final String INVALID_EXPORTED_SEQUENCE = "成功导出序号必须属于已分配范围";

    @Id
    @Column(name = "scope_id", length = 128, nullable = false)
    private String scopeId;
    @Column(name = "issuer_id", length = 64, nullable = false)
    private String issuerId;
    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;
    @Column(name = "next_sequence", nullable = false)
    private long nextSequence;
    @Column(name = "installed_sequence", nullable = false)
    private long installedSequence;
    @Column(name = "trust_version", nullable = false)
    private long trustVersion;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RevocationScopeEntity() {
    }

    public RevocationScopeEntity(String scopeId, String issuerId, String productId) {
        this.scopeId = scopeId;
        this.issuerId = issuerId;
        this.productId = productId;
        this.nextSequence = 0L;
        this.installedSequence = 0L;
        this.trustVersion = 0L;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getScopeId() { return scopeId; }
    public String getIssuerId() { return issuerId; }
    public String getProductId() { return productId; }
    public long getNextSequence() { return nextSequence; }
    public long getInstalledSequence() { return installedSequence; }
    public long getTrustVersion() { return trustVersion; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** 在范围行锁内分配不可复用的导出序号。 */
    public long allocateSequence() {
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("吊销范围序号耗尽");
        }
        nextSequence++;
        updatedAt = Instant.now();
        return nextSequence;
    }

    /** 为本产品范围分配单调递增的公钥信任资料版本。失败任务也不回收该版本。 */
    public long allocateTrustVersion() {
        if (trustVersion == Long.MAX_VALUE) {
            throw new IllegalStateException("吊销范围信任版本耗尽");
        }
        trustVersion++;
        updatedAt = Instant.now();
        return trustVersion;
    }

    /**
     * 在产品范围锁和 READY 包事务内确认最高成功导出序号；失败包不能调用，旧成功序号不能回退水位。
     *
     * <p>此水位只代表服务端已成功导出，不代表任何消费端已安装；不能超过本范围已分配序号。</p>
     */
    public void confirmExportedSequence(long sequence) {
        if (sequence <= 0 || sequence > nextSequence) {
            throw new IllegalArgumentException(INVALID_EXPORTED_SEQUENCE);
        }
        if (sequence > installedSequence) {
            installedSequence = sequence;
            updatedAt = Instant.now();
        }
    }
}
