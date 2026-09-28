package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * 身份源 owner 授权日志的 AKSK 本地游标与同步租约。
 */
@Data
@Entity
@Table(name = "aksk_owner_authorization_cursor")
public class AkskOwnerAuthorizationCursorEntity {

    @Id
    @Column(name = "stream_key", length = 64)
    private String streamKey;

    @Column(name = "last_source_sequence", nullable = false)
    private Long lastSourceSequence;

    /**
     * 只有此租约控制拉取 worker 的多实例领取。
     */
    @Column(name = "worker_lease_until")
    private Instant workerLeaseUntil;

    @Column(name = "worker_lease_owner", length = 64)
    private String workerLeaseOwner;

    /**
     * 身份源最近一次成功拉取授予的本地授权可用期限。
     */
    @Column(name = "synchronization_lease_until")
    private Instant synchronizationLeaseUntil;

    @Column(name = "last_successful_pull_at")
    private Instant lastSuccessfulPullAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
