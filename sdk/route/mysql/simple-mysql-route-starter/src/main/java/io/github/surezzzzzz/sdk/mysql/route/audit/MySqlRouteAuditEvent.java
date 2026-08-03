package io.github.surezzzzzz.sdk.mysql.route.audit;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * MySQL Route 脱敏审计事件。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class MySqlRouteAuditEvent {

    private final Instant occurredAt;
    private final String subject;
    private final String capability;
    private final String middlewareType;
    private final String datasource;
    private final String resourceDigest;
    private final int status;
    private final long durationMillis;
    private final String requestId;
}
