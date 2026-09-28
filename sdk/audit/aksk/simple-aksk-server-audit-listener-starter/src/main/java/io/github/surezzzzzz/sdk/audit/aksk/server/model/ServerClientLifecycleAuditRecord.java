package io.github.surezzzzzz.sdk.audit.aksk.server.model;

import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEventType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 已脱敏的 AKU 生命周期审计记录。
 */
@Getter
@Builder
public class ServerClientLifecycleAuditRecord {

    private final AkskClientLifecycleEventType eventType;
    private final Instant eventTime;
    private final String clientId;
    private final String ownerSourceId;
    private final String ownerSubjectId;
    private final Long targetApplicationId;
    private final Long lifecycleVersion;
}
