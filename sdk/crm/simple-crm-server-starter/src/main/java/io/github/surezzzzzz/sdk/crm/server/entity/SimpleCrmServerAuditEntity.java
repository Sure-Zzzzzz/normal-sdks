package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmAuditFact;
import lombok.Getter;

import java.time.Instant;

/**
 * 业务事实审计 JDBC 映射实体（写命令事实，永久保留）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerAuditEntity {

    private final String owner;
    private final String actionCode;
    private final String targetResourceTypeCode;
    private final String targetResourceId;
    private final long targetAggregateVersion;
    private final String correlationId;
    private final String resultCode;
    private final Instant occurredAt;

    /**
     * 创建SimpleCrmServerAuditEntity。
     *
     * @param owner                  归属主体（iam:/aksk: 前缀）
     * @param actionCode             CRM 操作稳定 code
     * @param targetResourceTypeCode 资源类型稳定 code
     * @param targetResourceId       资源唯一标识
     * @param targetAggregateVersion 聚合版本
     * @param correlationId          命令关联标识
     * @param resultCode             结果错误码（可空=成功）
     * @param occurredAt             发生时间
     */
    public SimpleCrmServerAuditEntity(String owner, String actionCode, String targetResourceTypeCode, String targetResourceId,
                       long targetAggregateVersion, String correlationId, String resultCode,
                       Instant occurredAt) {
        this.owner = owner;
        this.actionCode = actionCode;
        this.targetResourceTypeCode = targetResourceTypeCode;
        this.targetResourceId = targetResourceId;
        this.targetAggregateVersion = targetAggregateVersion;
        this.correlationId = correlationId;
        this.resultCode = resultCode;
        this.occurredAt = occurredAt;
    }

    /**
     * 实体转审计读取事实。
     *
     * @return 审计事实
     */
    public CrmAuditFact toFact() {
        return new CrmAuditFact(owner,
                io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction.fromCode(actionCode),
                io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType.fromCode(
                        targetResourceTypeCode),
                targetResourceId, targetAggregateVersion, correlationId, resultCode, occurredAt);
    }
}
