package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 中间件运维查询的脱敏审计事件。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsAuditEvent {

    /**
     * 事件产生时间。
     */
    private final Instant occurredAt;
    /**
     * 已认证主体标识。
     */
    private final String subject;
    /**
     * 固定运维能力。
     */
    private final MiddlewareOpsCapability capability;
    /**
     * 中间件类型。
     */
    private final MiddlewareType middlewareType;
    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 发生查询时的启动期数据源展示标签。
     */
    private final String clusterTag;
    /**
     * 资源范围的不可逆摘要。
     */
    private final String resourceDigest;
    /**
     * 完整受控操作参数快照。
     */
    private final MiddlewareOpsAuditContext context;
    /**
     * HTTP 结果状态。
     */
    private final int status;
    /**
     * 执行耗时毫秒。
     */
    private final long durationMillis;
    /**
     * 服务端请求标识。
     */
    private final String requestId;
}
