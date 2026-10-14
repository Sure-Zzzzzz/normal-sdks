package io.github.surezzzzzz.sdk.crm.server.core.domain.audit;

import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 已落库的业务审计事实（查询侧读取模型，与 CrmAuditPort 写入口径一致）。
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmAuditFact {

    private final String owner;
    private final CrmAction action;
    private final CrmResourceType targetResourceType;
    private final String targetResourceId;
    private final long targetAggregateVersion;
    private final String correlationId;
    private final String resultCode;
    private final Instant occurredAt;

    /**
     * 创建CrmAuditFact。
     *
     * @param owner                  归属主体（iam:/aksk: 前缀）
     * @param action                 已执行的 CRM 操作
     * @param targetResourceType     被操作资源类型
     * @param targetResourceId       被操作资源唯一标识
     * @param targetAggregateVersion 被操作聚合版本
     * @param correlationId          命令关联标识
     * @param resultCode             命令执行结果错误码（null=成功）
     * @param occurredAt             审计事实发生时间
     */
    public CrmAuditFact(String owner, CrmAction action, CrmResourceType targetResourceType,
                        String targetResourceId, long targetAggregateVersion, String correlationId,
                        String resultCode, Instant occurredAt) {
        this.owner = CrmValidationHelper.required(owner, "owner");
        this.action = CrmValidationHelper.requiredObject(action, "action");
        this.targetResourceType = CrmValidationHelper.requiredObject(targetResourceType, "targetResourceType");
        this.targetResourceId = CrmValidationHelper.required(targetResourceId, "targetResourceId");
        this.targetAggregateVersion = CrmValidationHelper.positiveVersion(targetAggregateVersion,
                "targetAggregateVersion");
        this.correlationId = CrmValidationHelper.required(correlationId, "correlationId");
        this.resultCode = CrmValidationHelper.optional(resultCode);
        this.occurredAt = CrmValidationHelper.requiredObject(occurredAt, "occurredAt");
    }
}
