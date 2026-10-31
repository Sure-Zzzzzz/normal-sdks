package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 部署激活（license_activation）：仅部署型授权的部署实例记录。
 *
 * <p>激活代数（activationGeneration）递增限制新签发；普通换机走 replacements 产生
 * REPLACED，风险换机另需独立权限。设备证据摘要在申请时固化。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_activation")
public class ActivationEntity {

    @Id
    @Column(name = "activation_id", length = 64, nullable = false)
    private String activationId;

    @Column(name = "grant_id", length = 64, nullable = false)
    private String grantId;

    /** 部署编码（客户侧部署实例标识）。 */
    @Column(name = "deployment_code", length = 128, nullable = false)
    private String deploymentCode;

    /** 激活代数。 */
    @Column(name = "activation_generation", nullable = false)
    private long activationGeneration;

    /** ACTIVE/SUSPENDED/REPLACED/REVOKED。 */
    @Column(name = "state", length = 16, nullable = false)
    private String state;

    /** 设备分量摘要（JSON 文本：分量名→SHA-256 hex；NONE 政策为空对象）。 */
    @Column(name = "component_digests", nullable = false, length = 4096)
    private String componentDigests;

    /** 管理端登记的获准调用者；旧行未绑定保持null，在线判定失败关闭。 */
    @lombok.Getter
    @Column(name = "caller_source_id", length = 128)
    private String callerSourceId;

    @lombok.Getter
    @Column(name = "caller_subject_id", length = 128)
    private String callerSubjectId;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ActivationEntity() {
    }

    public ActivationEntity(String activationId, String grantId, String deploymentCode,
                            String componentDigests) {
        this(activationId, grantId, deploymentCode, componentDigests, null, null);
    }

    /** 绑定仅由受管理权限保护的激活治理写入，在线请求不得就地回写。 */
    public ActivationEntity(String activationId, String grantId, String deploymentCode,
                            String componentDigests, String callerSourceId, String callerSubjectId) {
        this.activationId = activationId;
        this.grantId = grantId;
        this.deploymentCode = deploymentCode;
        this.activationGeneration = 1L;
        this.state = "ACTIVE";
        this.componentDigests = componentDigests == null || componentDigests.isEmpty()
                ? "{}" : componentDigests;
        this.callerSourceId = callerSourceId;
        this.callerSubjectId = callerSubjectId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getActivationId() {
        return activationId;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getDeploymentCode() {
        return deploymentCode;
    }

    public long getActivationGeneration() {
        return activationGeneration;
    }

    /** 换机新行沿用递增代数，旧记录不复用。 */
    public void setActivationGeneration(long activationGeneration) {
        this.activationGeneration = activationGeneration;
    }

    /** 受管理权限保护的显式绑定/改绑；调用方先核版本，不能由Decision请求触发。 */
    public void bindCaller(String sourceId, String subjectId) {
        this.callerSourceId = sourceId;
        this.callerSubjectId = subjectId;
        this.updatedAt = Instant.now();
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public String getComponentDigests() {
        return componentDigests;
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
