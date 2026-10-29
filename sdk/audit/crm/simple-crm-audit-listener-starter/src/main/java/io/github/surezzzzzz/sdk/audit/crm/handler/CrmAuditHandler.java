package io.github.surezzzzzz.sdk.audit.crm.handler;

import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;

/**
 * CRM 审计处理器接口
 *
 * <p>宿主实现此接口将审计记录落地到自有设施（Kafka、ES、MySQL 等）；
 * 至少存在一个实现时审计监听器才会注册。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface CrmAuditHandler {

    /**
     * 处理审计记录
     *
     * @param record 审计记录
     */
    void handle(CrmAuditRecord record);
}
