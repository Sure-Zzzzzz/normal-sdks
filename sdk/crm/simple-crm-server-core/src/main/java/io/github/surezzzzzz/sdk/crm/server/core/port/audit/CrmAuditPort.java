package io.github.surezzzzzz.sdk.crm.server.core.port.audit;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;

import java.time.Instant;

/**
 * 受保护命令审计端口。
 *
 * @author surezzzzzz
 */
public interface CrmAuditPort {

    /**
     * 记录受保护命令的审计结果。
     *
     * @param actor                  已认证且绑定租户的操作者
     * @param action                 已执行的 CRM 操作
     * @param targetResourceType     被操作资源的类型
     * @param targetResourceId       被操作资源的唯一标识
     * @param targetAggregateVersion 被操作聚合的权威版本
     * @param correlationId          命令关联标识
     * @param resultCode             命令执行结果错误码
     * @param occurredAt             审计事实发生时间
     */
    void record(CrmActor actor, CrmAction action, CrmResourceType targetResourceType,
                String targetResourceId, long targetAggregateVersion, String correlationId,
                CrmErrorCode resultCode, Instant occurredAt);
}
