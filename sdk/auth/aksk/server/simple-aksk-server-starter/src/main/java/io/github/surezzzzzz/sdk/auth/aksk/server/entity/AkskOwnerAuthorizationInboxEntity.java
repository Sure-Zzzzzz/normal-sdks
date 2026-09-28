package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * 身份源授权变更的 AKSK durable Inbox；eventId 是幂等边界。
 */
@Data
@Entity
@Table(name = "aksk_owner_authorization_inbox")
public class AkskOwnerAuthorizationInboxEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "source_sequence", nullable = false, unique = true)
    private Long sourceSequence;

    @Column(name = "change_type", length = 32, nullable = false)
    private String changeType;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;
}
