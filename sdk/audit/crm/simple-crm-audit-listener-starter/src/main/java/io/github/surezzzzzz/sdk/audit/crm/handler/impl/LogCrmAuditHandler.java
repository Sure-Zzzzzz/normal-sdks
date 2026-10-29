package io.github.surezzzzzz.sdk.audit.crm.handler.impl;

import io.github.surezzzzzz.sdk.audit.crm.annotation.SimpleCrmAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.crm.constant.SimpleCrmAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.crm.handler.CrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志审计处理器
 *
 * <p>将审计记录输出到日志，适用于开发测试环境，默认关闭；
 * 持久化类处理器由宿主后续按 {@code CrmAuditHandler} 扩展接入。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleCrmAuditListenerComponent
@ConditionalOnProperty(
        prefix = SimpleCrmAuditListenerConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = SimpleCrmAuditListenerConstant.ENABLED_PROPERTY,
        havingValue = SimpleCrmAuditListenerConstant.ENABLED_VALUE
)
public class LogCrmAuditHandler implements CrmAuditHandler {

    @Override
    public void handle(CrmAuditRecord record) {
        log.info("{} - {}", SimpleCrmAuditListenerConstant.LOG_EVENT_PREFIX, record);
    }
}
