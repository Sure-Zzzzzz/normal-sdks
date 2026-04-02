package io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider;

/**
 * ES Audit Trace ID Provider
 *
 * @author surezzzzzz
 */
public interface EsAuditTraceIdProvider {

    /**
     * Get trace ID
     */
    String getTraceId();
}
