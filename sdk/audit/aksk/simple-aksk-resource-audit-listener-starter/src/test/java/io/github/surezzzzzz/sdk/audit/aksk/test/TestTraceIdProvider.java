package io.github.surezzzzzz.sdk.audit.aksk.test;

import io.github.surezzzzzz.sdk.audit.aksk.resource.provider.AkskAuditTraceIdProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 测试用的 TraceId 提供者
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class TestTraceIdProvider implements AkskAuditTraceIdProvider {

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
