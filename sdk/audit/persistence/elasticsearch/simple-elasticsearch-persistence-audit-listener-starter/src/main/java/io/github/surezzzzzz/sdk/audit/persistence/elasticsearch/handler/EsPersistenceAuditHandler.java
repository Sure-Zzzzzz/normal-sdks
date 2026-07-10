package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;

/**
 * ES Persistence 审计处理器
 *
 * @author surezzzzzz
 */
public interface EsPersistenceAuditHandler {

    /**
     * 处理 persistence 审计记录
     *
     * @param record 审计记录
     */
    void handle(EsPersistenceAuditRecord record);
}
