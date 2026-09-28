package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * AKU 自助生命周期命令的幂等账本。
 *
 * <p>只保存请求指纹和结果 clientId，绝不保存 secret、令牌或授权快照。</p>
 */
@Data
@Entity
@Table(name = "aksk_client_lifecycle_command")
public class AkskClientLifecycleCommandEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "owner_source_id", length = 64, nullable = false, updatable = false)
    private String ownerSourceId;

    @Column(name = "owner_subject_id", length = 128, nullable = false, updatable = false)
    private String ownerSubjectId;

    @Column(name = "command_type", length = 32, nullable = false, updatable = false)
    private String commandType;

    @Column(name = "idempotency_key", length = 128, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64, nullable = false, updatable = false)
    private String requestFingerprint;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
