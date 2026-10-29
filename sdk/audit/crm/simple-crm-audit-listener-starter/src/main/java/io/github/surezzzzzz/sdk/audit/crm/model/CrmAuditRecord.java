package io.github.surezzzzzz.sdk.audit.crm.model;

import lombok.Builder;
import lombok.Value;

/**
 * CRM 业务审计记录
 *
 * <p>由提交后审计事件投影而来，分发给宿主处理器；字段与 {@code CrmAuditEvent}
 * 一一对应，不携带命令参数与业务快照。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Value
@Builder
public class CrmAuditRecord {

    /**
     * 审计类型（与审计行动作码一致，如 customer_create）
     */
    private String eventType;

    /**
     * 事件唯一标识
     */
    private String eventId;

    /**
     * 事件版本
     */
    private Integer eventVersion;

    /**
     * 发生时间（毫秒纪元）
     */
    private Long timestamp;

    /**
     * 归属主体（iam:/aksk: 前缀坐标）
     */
    private String owner;

    /**
     * 目标资源类型码（如 customer）
     */
    private String resourceTypeCode;

    /**
     * 目标资源标识
     */
    private String resourceId;

    /**
     * 目标聚合版本
     */
    private Long aggregateVersion;

    /**
     * 命令关联标识
     */
    private String correlationId;

    /**
     * 结果码（成功审计为 null）
     */
    private String resultCode;

    /**
     * 链路追踪ID（提供者缺省时为 null）
     */
    private String traceId;
}
