package io.github.surezzzzzz.sdk.audit.license.handler.impl;

import io.github.surezzzzzz.sdk.audit.license.annotation.SimpleLicenseAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志审计处理器
 *
 * <p>将审计记录输出到日志，适用于开发测试环境，默认关闭。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleLicenseAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.license.listener.handler.log",
        name = "enabled",
        havingValue = "true"
)
public class LogLicenseAuditHandler implements LicenseAuditHandler {

    @Override
    public void handle(LicenseAuditRecord record) {
        log.info("LICENSE_AUDIT - {}", record);
    }
}
