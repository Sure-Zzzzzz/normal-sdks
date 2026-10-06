package io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * 外部服务对普通角色的固定管理边界；删除后保留幂等墓碑。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_open_role_binding")
public class IamOpenRoleBindingEntity {
    /**
     * 对外稳定 UUID。
     */
    @Id
    @Column(name = "open_role_id", length = 36, nullable = false, columnDefinition = "char(36)")
    private String openRoleId;
    /**
     * 普通角色编号，不作为外部身份。
     */
    @Column(name = "role_id", nullable = false, unique = true)
    private Long roleId;
    /**
     * 已验证主体来源。
     */
    @Column(name = "owner_source_id", length = 128, nullable = false)
    private String ownerSourceId;
    /**
     * 已验证主体类型。
     */
    @Column(name = "owner_subject_type", length = 16, nullable = false)
    private String ownerSubjectType;
    /**
     * 已验证稳定主体编号。
     */
    @Column(name = "owner_subject_id", length = 256, nullable = false)
    private String ownerSubjectId;
    /**
     * 调用方创建幂等 UUID。
     */
    @Column(name = "external_id", length = 36, nullable = false, columnDefinition = "char(36)")
    private String externalId;
    /**
     * 创建后固定的目标应用。
     */
    @Column(name = "application_id", nullable = false)
    private Long applicationId;
    /**
     * 创建后固定的部门根节点。
     */
    @Column(name = "root_department_id", nullable = false)
    private Long rootDepartmentId;
    /**
     * 规范化创建内容摘要。
     */
    @Column(name = "creation_digest", length = 64, nullable = false, columnDefinition = "char(64)")
    private String creationDigest;
    /**
     * 所有管理路径共享的修改版本，不使用缓存判定。
     */
    @Column(name = "revision", nullable = false)
    private Long revision;
    /**
     * ACTIVE 或 DELETED。
     */
    @Column(name = "state", length = 16, nullable = false)
    private String state;
    /**
     * 创建时间。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    /**
     * 最近实际变更时间。
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
