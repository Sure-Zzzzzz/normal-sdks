package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditTraceIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 测试用的 TraceId 提供者
 *
 * <p>只在单元测试中使用
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "use-mock-provider",
        havingValue = "true"
)
public class TestTraceIdProvider implements EsAuditTraceIdProvider {

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
