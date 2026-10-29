package io.github.surezzzzzz.sdk.audit.crm.test;

import io.github.surezzzzzz.sdk.audit.crm.provider.CrmAuditTraceIdProvider;
import org.springframework.stereotype.Component;

/**
 * 测试用的 CRM 审计链路追踪ID提供者
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
public class TestCrmAuditTraceIdProvider implements CrmAuditTraceIdProvider {

    /**
     * 测试链路追踪ID。
     */
    public static final String TRACE_ID = "trace-crm-example-001";

    private static volatile boolean fail;

    /** 控制测试提供者模拟扩展失败。 */
    public static void setFail(boolean value) {
        fail = value;
    }

    @Override
    public String getTraceId() {
        if (fail) {
            throw new IllegalStateException("trace provider test failure");
        }
        return TRACE_ID;
    }
}
