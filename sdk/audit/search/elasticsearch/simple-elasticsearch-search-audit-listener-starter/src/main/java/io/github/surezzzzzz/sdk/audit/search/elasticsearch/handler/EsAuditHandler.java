package io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;

/**
 * ES Audit Handler
 *
 * @author surezzzzzz
 */
public interface EsAuditHandler {

    /**
     * Handle audit record
     *
     * @param record audit record
     */
    void handle(EsAuditRecord record);
}
