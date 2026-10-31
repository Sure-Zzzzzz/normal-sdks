package io.github.surezzzzzz.sdk.license.server.entity;

import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.constant.GrantValidityConstant;
import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 客户授权（grant）持久化实体：客户/产品/对象种类/资格政策/模式固定于主行。
 *
 * <p>商业修订内容（权益/期限快照）在修订表；管理状态与 rowVersion 承载乐观锁。
 * UTF-8 中文注释字段与显式索引在 schema 脚本统一维护。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_grant")
public class GrantRevisionEntity {

    @Id
    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    @Column(name = "customer_id", length = 128, nullable = false)
    private String customerId;

    @Column(name = "product_id", length = 128, nullable = false)
    private String productId;

    @Column(name = "subject_kind", length = 16, nullable = false)
    private String subjectKind;

    @Column(name = "eligibility_kind", length = 32, nullable = false)
    private String eligibilityKind;

    @Column(name = "offline_proof_profile", length = 96)
    private String offlineProofProfile;

    @Column(name = "mode", length = 16, nullable = false)
    private String mode;

    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "grant_revision", nullable = false)
    private long grantRevision;

    @Column(name = "grant_state_version", nullable = false)
    private long grantStateVersion;

    @Column(name = "business_order_ref", length = 128)
    private String businessOrderRef;

    /** 引用的已发布模型版本（可空：无模型产品的授权与存量行）。 */
    @Column(name = "model_version_id", length = 128)
    private String modelVersionId;

    /** 权益值快照（JSON 对象：字段名→整数；键集须为模型 rights 声明子集）。 */
    @Column(name = "rights_json", columnDefinition = "TEXT")
    private String rightsJson;

    /** 客户 OpenAPI 限频授权快照；由 License 履约适配器投影到 limiter Management。 */
    @Column(name = "limiter_policies_json", columnDefinition = "TEXT")
    private String limiterPoliciesJson;

    /** 期限业务事实（持久列；在线判定与离线签发共用同一事实源）。 */
    @Column(name = "validity_kind", length = 16)
    private String validityKind;

    @Column(name = "starts_at")
    private Long startsAt;

    @Column(name = "service_until")
    private Long serviceUntil;

    @Column(name = "grace_until")
    private Long graceUntil;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected GrantRevisionEntity() {
    }

    public GrantRevisionEntity(String grantId, String customerId, String productId, String subjectKind,
                               String eligibilityKind, String offlineProofProfile, String mode,
                               String businessOrderRef) {
        this(grantId, customerId, productId, subjectKind, eligibilityKind, offlineProofProfile,
                mode, businessOrderRef, null, null);
    }

    public GrantRevisionEntity(String grantId, String customerId, String productId, String subjectKind,
                               String eligibilityKind, String offlineProofProfile, String mode,
                               String businessOrderRef, String modelVersionId, String rightsJson) {
        this(grantId, customerId, productId, subjectKind, eligibilityKind, offlineProofProfile, mode,
                businessOrderRef, modelVersionId, rightsJson, null);
    }

    public GrantRevisionEntity(String grantId, String customerId, String productId, String subjectKind,
                               String eligibilityKind, String offlineProofProfile, String mode,
                               String businessOrderRef, String modelVersionId, String rightsJson,
                               String limiterPoliciesJson) {
        this.grantId = grantId;
        this.customerId = customerId;
        this.productId = productId;
        this.subjectKind = subjectKind;
        this.eligibilityKind = eligibilityKind;
        this.offlineProofProfile = offlineProofProfile;
        this.mode = mode;
        this.state = "DRAFT";
        this.grantRevision = 1L;
        this.grantStateVersion = 1L;
        this.businessOrderRef = businessOrderRef;
        this.modelVersionId = modelVersionId;
        this.rightsJson = rightsJson;
        this.limiterPoliciesJson = limiterPoliciesJson;
        // 默认期限只在新建时确定；JPA 无参装载不补值，旧行缺事实必须经受控迁移处理。
        long createdAt = Instant.now().getEpochSecond();
        this.validityKind = ValidityPolicy.Kind.FIXED.getCode();
        this.startsAt = createdAt;
        this.serviceUntil = createdAt + GrantValidityConstant.DEFAULT_SERVICE_SECONDS;
        this.graceUntil = this.serviceUntil + GrantValidityConstant.DEFAULT_GRACE_SECONDS;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getSubjectKind() {
        return subjectKind;
    }

    public String getEligibilityKind() {
        return eligibilityKind;
    }

    public String getOfflineProofProfile() {
        return offlineProofProfile;
    }

    public String getMode() {
        return mode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public void setGrantRevision(long grantRevision) {
        this.grantRevision = grantRevision;
    }

    public long getGrantStateVersion() {
        return grantStateVersion;
    }

    public void setGrantStateVersion(long grantStateVersion) {
        this.grantStateVersion = grantStateVersion;
    }

    public String getBusinessOrderRef() {
        return businessOrderRef;
    }

    public String getModelVersionId() {
        return modelVersionId;
    }

    public String getRightsJson() {
        return rightsJson;
    }

    public String getLimiterPoliciesJson() {
        return limiterPoliciesJson;
    }

    public String getValidityKind() {
        return validityKind;
    }

    public Long getStartsAt() {
        return startsAt;
    }

    public Long getServiceUntil() {
        return serviceUntil;
    }

    public Long getGraceUntil() {
        return graceUntil;
    }

    /** 显式期限覆盖新建默认值，随授权行固化；永久期限的两个截止必须传 null。 */
    public GrantRevisionEntity withValidity(String kind, Long startsAt, Long serviceUntil, Long graceUntil) {
        this.validityKind = kind;
        this.startsAt = startsAt;
        this.serviceUntil = serviceUntil;
        this.graceUntil = graceUntil;
        return this;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
