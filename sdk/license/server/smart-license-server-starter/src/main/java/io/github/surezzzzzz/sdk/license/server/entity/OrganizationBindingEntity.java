package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 客户组织绑定（IAM 部门准入事实源）：客户 ↔ IAM 组织实例+授权根部门。
 *
 * <p>绑定存在即启用部门准入：CUSTOMER_MEMBERS 在线判定按此行回查 IAM 目录成员
 * 归属（子树内=准入）；无绑定客户不启用目录准入（规则链照常）。绑定代数随重绑递增，
 * 联动清单以代数识别过期差异。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_organization_binding")
public class OrganizationBindingEntity {

    @Id
    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "organization_instance_id", length = 128, nullable = false)
    private String organizationInstanceId;

    @Column(name = "root_department_id", nullable = false)
    private long rootDepartmentId;

    @Column(name = "binding_generation", nullable = false)
    private int bindingGeneration;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected OrganizationBindingEntity() {
    }

    public OrganizationBindingEntity(String customerId, String organizationInstanceId,
                                     long rootDepartmentId) {
        this.customerId = customerId;
        this.organizationInstanceId = organizationInstanceId;
        this.rootDepartmentId = rootDepartmentId;
        this.bindingGeneration = 1;
        this.createdAt = Instant.now();
    }

    /** 重绑：组织实例或根部门变化时更新并递增代数（旧联动清单按代数失效）。 */
    public void rebind(String organizationInstanceId, long rootDepartmentId) {
        this.organizationInstanceId = organizationInstanceId;
        this.rootDepartmentId = rootDepartmentId;
        this.bindingGeneration++;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getOrganizationInstanceId() {
        return organizationInstanceId;
    }

    public long getRootDepartmentId() {
        return rootDepartmentId;
    }

    public int getBindingGeneration() {
        return bindingGeneration;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
