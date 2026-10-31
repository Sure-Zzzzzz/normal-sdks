package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

/**
 * 产品记录（license_product）：签发方产品线，声明动作总集与能力清单。
 *
 * <p>产品自己声明动作（如 channel.create），平台不猜测读写语义；
 * declaredActions/supportedCapabilities 为去重 JSON 数组文本（模型快照签入凭据）。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_product", uniqueConstraints = @UniqueConstraint(
        name = "uk_license_product_issuer_code", columnNames = {"issuer_id", "code"}))
public class ProductEntity {

    @Id
    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "issuer_id", length = 64, nullable = false)
    private String issuerId;

    /** 业务编码（签发方内唯一）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "display_name", length = 128, nullable = false)
    private String displayName;

    /** 声明动作总集（JSON 数组文本，发布模型引用其子集）。 */
    @Column(name = "declared_actions", nullable = false, length = 8192)
    private String declaredActions;

    /** 支持能力清单（JSON 数组文本，license-condition/1 等）。 */
    @Column(name = "supported_capabilities", nullable = false, length = 4096)
    private String supportedCapabilities;

    /** 门户应用绑定项（JSON 数组：[{applicationId, basePermissionSnapshotVersion}]）。 */
    @Column(name = "application_bindings", columnDefinition = "TEXT")
    private String applicationBindings;

    /** 1=有效 0=停用。 */
    @Column(name = "status", nullable = false)
    private int status;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProductEntity() {
    }

    public ProductEntity(String productId, String issuerId, String code, String displayName,
                         String declaredActions, String supportedCapabilities) {
        this.productId = productId;
        this.issuerId = issuerId;
        this.code = code;
        this.displayName = displayName;
        this.declaredActions = declaredActions;
        this.supportedCapabilities = supportedCapabilities;
        this.status = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getProductId() {
        return productId;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeclaredActions() {
        return declaredActions;
    }

    public String getApplicationBindings() {
        return applicationBindings;
    }

    public void setApplicationBindings(String applicationBindings) {
        this.applicationBindings = applicationBindings;
    }

    public String getSupportedCapabilities() {
        return supportedCapabilities;
    }

    /** 仅允许通过产品修订接口扩展能力清单，避免业务层直接改写产品事实。 */
    public void setSupportedCapabilities(String supportedCapabilities) {
        this.supportedCapabilities = supportedCapabilities;
        this.updatedAt = Instant.now();
    }

    public int getStatus() {
        return status;
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
