package io.github.surezzzzzz.sdk.audit.crm.test;

import io.github.surezzzzzz.sdk.audit.crm.handler.CrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 始终失败的测试审计处理器
 *
 * <p>用于验证单个处理器异常不影响其余处理器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class FailingCrmAuditHandler implements CrmAuditHandler {

    public static final AtomicInteger INVOCATIONS = new AtomicInteger();

    /**
     * 处理器故障文本。
     */
    private static final String HANDLER_FAILURE_MESSAGE = "测试CRM审计处理器失败";

    @Override
    public void handle(CrmAuditRecord record) {
        INVOCATIONS.incrementAndGet();
        log.info("注入处理器故障：eventType={}", record.getEventType());
        throw new IllegalStateException(HANDLER_FAILURE_MESSAGE);
    }
}
