package io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider;

/**
 * 审计追踪标识提供器
 *
 * @author surezzzzzz
 */
public interface EsAuditTraceIdProvider {

    /**
     * 获取当前追踪标识
     */
    String getTraceId();
}
