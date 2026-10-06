package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider;

/**
 * ES Persistence 审计链路追踪 ID 提供器
 *
 * @author surezzzzzz
 */
public interface EsPersistenceAuditTraceIdProvider {

    /**
     * 获取链路追踪 ID
     *
     * @return 链路追踪 ID
     */
    String getTraceId();
}
