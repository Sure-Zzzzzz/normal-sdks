package io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;

/**
 * Elasticsearch Search 审计处理器
 *
 * @author surezzzzzz
 */
public interface EsAuditHandler {

    /**
     * 处理审计记录
     *
     * @param record 审计记录
     */
    void handle(EsAuditRecord record);
}
