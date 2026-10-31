package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 授权方案版本（license_plan_version）：模型版本的权益预填模板。
 *
 * <p>方案只预填，授权保存完整展开值（不依赖方案后续变化）；
 * DRAFT 可编辑，PUBLISHED 不可变，RETIRED 禁止新引用。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_plan_version")
public class PlanVersionEntity {

    @Id
    @Column(name = "plan_version_id", length = 64, nullable = false)
    private String planVersionId;

    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    /** 引用的已发布模型版本（精确 modelVersionId）。 */
    @Column(name = "model_version_id", length = 64, nullable = false)
    private String modelVersionId;

    /** 方案内递增版本。 */
    @Column(name = "version", nullable = false)
    private long version;

    /** DRAFT/PUBLISHED/RETIRED。 */
    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PublicationState status;

    /** 预填权益 JSON（rights 对象；签发前显式展开进授权）。 */
    @Column(name = "prefill_rights", nullable = false, length = 65536)
    private String prefillRights;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlanVersionEntity() {
    }

    public PlanVersionEntity(String planVersionId, String planId, String productId,
                             String modelVersionId, long version, String prefillRights) {
        this.planVersionId = planVersionId;
        this.planId = planId;
        this.productId = productId;
        this.modelVersionId = modelVersionId;
        this.version = version;
        this.status = PublicationState.DRAFT;
        this.prefillRights = prefillRights;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    public void publish() {
        this.status = status.publish();
        this.updatedAt = Instant.now();
    }

    public void updatePrefill(String prefillRights) {
        if (!status.editable()) {
            throw new IllegalStateException("已发布方案不可变");
        }
        this.prefillRights = prefillRights;
        this.updatedAt = Instant.now();
    }

    public String getPlanVersionId() {
        return planVersionId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getProductId() {
        return productId;
    }

    public String getModelVersionId() {
        return modelVersionId;
    }

    public long getVersion() {
        return version;
    }

    public String getStatus() {
        return status.getCode();
    }

    public String getPrefillRights() {
        return prefillRights;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
