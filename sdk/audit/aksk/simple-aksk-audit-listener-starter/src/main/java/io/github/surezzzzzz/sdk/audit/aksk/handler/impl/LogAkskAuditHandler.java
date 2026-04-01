package io.github.surezzzzzz.sdk.audit.aksk.handler.impl;

import io.github.surezzzzzz.sdk.audit.aksk.annotation.SimpleAkskAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.model.AkskAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志审计处理器
 *
 * <p>将审计记录输出到日志，适用于开发测试环境。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleAkskAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.aksk.listener.handler.log",
        name = "enabled",
        havingValue = "true"
)
public class LogAkskAuditHandler implements AkskAuditHandler {

    @Override
    public void handle(AkskAuditRecord record) {
        log.info("AKSK_AUDIT - {}", record);
    }
}
