package io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 授权控制面变更日志。
 *
 * <p>主键同时是全局单调 sourceSequence。该表只保存提交后的最终状态，
 * 不承担向 AKSK 推送的职责。</p>
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_aksk_authorization_change")
public class IamAkskAuthorizationChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_sequence")
    private Long sourceSequence;

    @Column(name = "event_id", length = 36, nullable = false, unique = true, updatable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 32, nullable = false)
    private IamAkskAuthorizationChangeType changeType;

    @Column(name = "aggregate_key", length = 320, nullable = false)
    private String aggregateKey;

    @Column(name = "reason_code", length = 64, nullable = false)
    private String reasonCode;

    @Column(name = "schema_version", nullable = false)
    private Long schemaVersion = 1L;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
