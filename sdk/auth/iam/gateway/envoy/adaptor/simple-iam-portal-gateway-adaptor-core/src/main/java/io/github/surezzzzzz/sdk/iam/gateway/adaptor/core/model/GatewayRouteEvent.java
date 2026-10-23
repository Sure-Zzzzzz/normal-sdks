package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 控制面脱敏事件。事件消费失败不得回传主同步流程。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class GatewayRouteEvent {

    /**
     * 事件契约版本。
     */
    @Builder.Default
    String schemaVersion = SimpleIamPortalGatewayAdaptorConstant.EVENT_SCHEMA_VERSION;
    /**
     * 事件唯一编号。
     */
    String eventId;
    /**
     * 同步链路关联编号。
     */
    String correlationId;
    /**
     * 事件类型。
     */
    GatewayRouteEventType type;
    /**
     * IAM 目录版本。
     */
    Long catalogRevision;
    /**
     * 快照版本。
     */
    String snapshotVersion;
    /**
     * 快照路由数量。
     */
    int routeCount;
    /**
     * 脱敏目标摘要。
     */
    String targetDigest;
    /**
     * 结构化原因码。
     */
    String reasonCode;
    /**
     * 事件发生时间。
     */
    Instant occurredAt;
}
