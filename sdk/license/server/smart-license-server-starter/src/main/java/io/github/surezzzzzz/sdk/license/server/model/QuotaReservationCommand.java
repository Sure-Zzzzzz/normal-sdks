package io.github.surezzzzzz.sdk.license.server.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 在线额度命令；认证字段由边界上下文填入，禁止从请求体覆盖。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class QuotaReservationCommand {

    private final String productId;
    private final String grantId;
    private final String subjectKind;
    private final String action;
    private final String metric;
    private final long quantity;
    private final String kind;
    private final String businessOperationId;
    private final long expectedGrantRevision;
    private final String principalId;
    private final String authenticationSourceId;
    private final String subjectType;
    private final String assignmentId;
    private final String activationId;
}
