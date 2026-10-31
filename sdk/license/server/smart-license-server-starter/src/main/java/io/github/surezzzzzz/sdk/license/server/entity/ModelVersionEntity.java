package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 模型版本（license_model_version）：产品下条件树/字段/动作的版本化快照。
 *
 * <p>DRAFT 可编辑；PUBLISHED 不可变（签发凭据引用其完整快照）；RETIRED 禁止新引用。
 * 快照原文（JSON）按发布时字节固化，凭据签入即此字节。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_model_version")
public class ModelVersionEntity {

    @Id
    @Column(name = "model_version_id", length = 64, nullable = false)
    private String modelVersionId;

    @Column(name = "model_id", length = 64, nullable = false)
    private String modelId;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    /** 模型内递增版本（产品内 model+version 唯一）。 */
    @Column(name = "version", nullable = false)
    private long version;

    /** DRAFT/PUBLISHED/RETIRED。 */
    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PublicationState status;

    /** 规则快照原文（license-condition/1：fields+actions 完整树；发布时字节固化）。 */
    @Column(name = "snapshot", nullable = false, length = 262144)
    private String snapshot;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ModelVersionEntity() {
    }

    public ModelVersionEntity(String modelVersionId, String modelId, String productId,
                              long version, String snapshot) {
        this.modelVersionId = modelVersionId;
        this.modelId = modelId;
        this.productId = productId;
        this.version = version;
        this.status = PublicationState.DRAFT;
        this.snapshot = snapshot;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    /** 发布：DRAFT→PUBLISHED（不可逆；快照自此只读）。 */
    public void publish() {
        this.status = status.publish();
        this.updatedAt = Instant.now();
    }

    /** 退役：PUBLISHED→RETIRED（禁止新引用；历史凭据不受影响）。 */
    public void retire() {
        this.status = status.retire();
        this.updatedAt = Instant.now();
    }

    public String getModelVersionId() {
        return modelVersionId;
    }

    public String getModelId() {
        return modelId;
    }

    public String getProductId() {
        return productId;
    }

    public long getVersion() {
        return version;
    }

    public String getStatus() {
        return status.getCode();
    }

    /** 草稿编辑快照（仅 DRAFT 可改；已发布不可变由状态机守卫）。 */
    public void updateSnapshot(String snapshot) {
        if (!status.editable()) {
            throw new IllegalStateException("已发布快照不可变");
        }
        this.snapshot = snapshot;
        this.updatedAt = Instant.now();
    }

    public String getSnapshot() {
        return snapshot;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
