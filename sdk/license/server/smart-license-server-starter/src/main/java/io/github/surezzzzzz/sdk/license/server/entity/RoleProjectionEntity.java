package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import lombok.Getter;

/**
 * License 自有专用角色台账：环境与绑定代数属于治理事实，外部编号不能跨环境自动复用。
 * 最后核验快照用于漂移检测，不把同名角色或 code 前缀当作所有权证明。
 *
 * @author surezzzzzz
 */
@Getter
@Entity
@Table(name = "license_role_projection", uniqueConstraints = @UniqueConstraint(
        name = "uk_license_projection_target", columnNames = {"customer_id", "product_id", "application_id"}))
public class RoleProjectionEntity {
    @Id
    @Column(name = "projection_id", length = 64, nullable = false)
    private String projectionId;
    @Column(name = "external_id", length = 64, nullable = false, unique = true)
    private String externalId;
    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;
    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;
    @Column(name = "application_id", nullable = false)
    private long applicationId;
    @Column(name = "organization_instance_id", length = 128, nullable = false)
    private String organizationInstanceId;
    @Column(name = "binding_generation", nullable = false)
    private int bindingGeneration;
    @Column(name = "root_department_id", nullable = false)
    private long rootDepartmentId;
    @Column(name = "open_role_id", length = 64)
    private String openRoleId;
    @Column(name = "role_code", length = 128)
    private String roleCode;
    @Column(name = "desired_revision", nullable = false)
    private long desiredRevision;
    @Column(name = "acknowledged_rules", columnDefinition = "TEXT")
    private String acknowledgedRules;
    @Column(name = "acknowledged_departments", columnDefinition = "TEXT")
    private String acknowledgedDepartments;
    @Column(name = "remote_revision")
    private Long remoteRevision;
    @Column(name = "state", length = 40, nullable = false)
    private String state;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected RoleProjectionEntity() { }

    /** 第一份计划建立稳定 externalId，后续同目标复用且只递增期望修订。 */
    public RoleProjectionEntity(String projectionId, String customerId, String productId, long applicationId,
                                OrganizationBindingEntity binding) {
        this.projectionId = projectionId;
        this.externalId = projectionId;
        this.customerId = customerId;
        this.productId = productId;
        this.applicationId = applicationId;
        this.organizationInstanceId = binding.getOrganizationInstanceId();
        this.bindingGeneration = binding.getBindingGeneration();
        this.rootDepartmentId = binding.getRootDepartmentId();
        mark("PENDING_EXECUTION");
    }

    /** 本地期望修订只在持有该台账数据库写锁时推进。 */
    public long nextRevision() { updatedAt = Instant.now(); return ++desiredRevision; }

    /** 外部创建结果绑定一次；不能用新返回的同名角色替换已绑定实际身份。 */
    public void bind(String roleId, String code) {
        if (openRoleId != null && !openRoleId.equals(roleId)) {
            throw new IllegalStateException("专用角色身份不匹配");
        }
        openRoleId = roleId;
        roleCode = code;
        updatedAt = Instant.now();
    }

    /** 每个已知写入后的实际快照持久化；下一次执行先与其比对。 */
    public void acknowledge(String rules, String departments, long revision) {
        acknowledgedRules = rules;
        acknowledgedDepartments = departments;
        remoteRevision = revision;
        updatedAt = Instant.now();
    }

    /** 故障状态持久化，使新计划不能抹掉未知写入或漂移保护。 */
    public void mark(String state) { this.state = state; updatedAt = Instant.now(); }

    /** 显式确认新环境后分配新外部幂等身份，旧角色编号和规则绝不自动续用。 */
    public void reconfirmEnvironment(OrganizationBindingEntity binding, String newExternalId) {
        organizationInstanceId = binding.getOrganizationInstanceId();
        bindingGeneration = binding.getBindingGeneration();
        rootDepartmentId = binding.getRootDepartmentId();
        externalId = newExternalId;
        openRoleId = null;
        roleCode = null;
        acknowledgedRules = null;
        acknowledgedDepartments = null;
        remoteRevision = null;
        mark(IntegrationState.PENDING_EXECUTION.getCode());
    }
}
