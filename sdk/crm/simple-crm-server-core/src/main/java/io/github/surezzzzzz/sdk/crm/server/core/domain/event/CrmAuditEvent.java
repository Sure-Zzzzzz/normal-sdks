package io.github.surezzzzzz.sdk.crm.server.core.domain.event;

import lombok.Getter;

import java.time.Instant;

/**
 * CRM 业务命令审计事件（进程内、事务提交后发布）。
 *
 * <p>这是 core 对外提供的稳定事件契约。字段与已提交审计事实一一对应，
 * 不携带命令参数或业务快照；事件只应在事实提交后发布。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmAuditEvent {

    /**
     * 首发事件契约版本；字段演进时递增。
     */
    public static final int CURRENT_EVENT_VERSION = 1;

    private final String eventId;
    private final int eventVersion;
    private final String owner;
    private final String actionCode;
    private final String resourceTypeCode;
    private final String resourceId;
    private final long aggregateVersion;
    private final String correlationId;
    private final String resultCode;
    private final Instant occurredAt;

    /**
     * 创建已提交 CRM 审计事件。
     *
     * @param eventId          事件唯一标识
     * @param eventVersion     事件契约版本
     * @param owner            归属主体坐标
     * @param actionCode       审计动作码
     * @param resourceTypeCode 目标资源类型码
     * @param resourceId       目标资源标识
     * @param aggregateVersion 目标聚合版本
     * @param correlationId    命令关联标识
     * @param resultCode       结果码，成功时为空
     * @param occurredAt       发生时间
     */
    public CrmAuditEvent(String eventId, int eventVersion, String owner, String actionCode,
                         String resourceTypeCode, String resourceId, long aggregateVersion,
                         String correlationId, String resultCode, Instant occurredAt) {
        this.eventId = eventId;
        this.eventVersion = eventVersion;
        this.owner = owner;
        this.actionCode = actionCode;
        this.resourceTypeCode = resourceTypeCode;
        this.resourceId = resourceId;
        this.aggregateVersion = aggregateVersion;
        this.correlationId = correlationId;
        this.resultCode = resultCode;
        this.occurredAt = occurredAt;
    }
}
