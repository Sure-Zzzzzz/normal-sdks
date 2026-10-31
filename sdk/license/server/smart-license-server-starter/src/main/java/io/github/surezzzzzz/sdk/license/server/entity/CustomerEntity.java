package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 签发方记录（license_customer）：买方企业记录：商业授权的归属客户，组织绑定与成员资格的客户侧主体。
 *
 * <p>稳定编号业务生成；code 全局唯一；rowVersion 乐观锁（修订围栏）。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_customer")
public class CustomerEntity {

    @Id
    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    /** 业务编码（唯一，ASCII）。 */
    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", length = 128, nullable = false)
    private String displayName;

    /** 1=有效 0=停用（停用禁止新建产品，既有凭据不受影响）。 */
    @Column(name = "status", nullable = false)
    private int status;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CustomerEntity() {
    }

    public CustomerEntity(String customerId, String code, String displayName) {
        this.customerId = customerId;
        this.code = code;
        this.displayName = displayName;
        this.status = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
        this.updatedAt = Instant.now();
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
