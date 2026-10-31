package io.github.surezzzzzz.sdk.audit.license.test;

import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
public class FailingLicenseAuditHandler implements LicenseAuditHandler {

    /**
     * 处理器故障文本。
     */
    private static final String HANDLER_FAILURE_MESSAGE = "测试License审计处理器失败";

    @Override
    public void handle(LicenseAuditRecord record) {
        log.info("注入处理器故障：eventType={}", record.getEventType());
        throw new IllegalStateException(HANDLER_FAILURE_MESSAGE);
    }
}
