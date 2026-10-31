package io.github.surezzzzzz.sdk.audit.license.test;

import io.github.surezzzzzz.sdk.audit.license.provider.LicenseAuditTraceIdProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 测试用的 License 审计 TraceId 提供者
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class TestLicenseAuditTraceIdProvider implements LicenseAuditTraceIdProvider {

    public String nextTraceId;
    private String traceId;

    @Override
    public String getTraceId() {
        return nextTraceId != null ? nextTraceId : traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void reset() {
        this.traceId = null;
    }
}
