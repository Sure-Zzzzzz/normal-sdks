package io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * 可信应用异步删除操作。
 *
 * <p>applicationId 仅是审计快照，不建立外键，保证应用主数据物理删除后仍可查询操作结果。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_trusted_application_cleanup_operation")
public class IamTrustedApplicationCleanupOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private TrustedApplicationCleanupOperationState state;

    @Column(name = "registered_client_ids_json", nullable = false, columnDefinition = "LONGTEXT")
    private String registeredClientIdsJson;

    @Column(name = "cursor_value", nullable = false)
    private Long cursorValue;

    @Column(name = "attempt_count", nullable = false)
    private Long attemptCount;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    /**
     * 当前租约的随机持有标识，仅用于防止过期 worker 覆盖接管者的结果，不向管理 API 暴露。
     */
    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
