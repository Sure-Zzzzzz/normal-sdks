package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditTraceIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 测试用 persistence 审计 TraceId 提供者
 *
 * @author surezzzzzz
 */
@Component
@ConditionalOnProperty(prefix = "test.es.persistence.audit", name = "use-mock-provider", havingValue = "true")
public class TestPersistenceAuditTraceIdProvider implements EsPersistenceAuditTraceIdProvider {

    private String traceId;

    @Override
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void reset() {
        this.traceId = null;
    }
}
