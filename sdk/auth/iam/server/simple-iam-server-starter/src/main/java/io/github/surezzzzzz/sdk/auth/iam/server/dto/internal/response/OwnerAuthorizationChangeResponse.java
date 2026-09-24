package io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * IAM 授权变更日志的单条最终态快照。
 */
@Data
@Builder
public class OwnerAuthorizationChangeResponse {

    private Long sourceSequence;
    private String eventId;
    private IamAkskAuthorizationChangeType changeType;
    private String aggregateKey;
    private String reasonCode;
    private Long schemaVersion;
    private Map<String, Object> payload;
    private Long occurredAt;
}
