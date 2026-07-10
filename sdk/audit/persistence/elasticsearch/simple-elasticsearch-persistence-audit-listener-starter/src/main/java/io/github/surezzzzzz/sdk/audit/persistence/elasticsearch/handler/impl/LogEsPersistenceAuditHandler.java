package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.impl;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.annotation.PersistenceAuditComponent;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant.PersistenceAuditConstant;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * ES Persistence 日志审计处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@PersistenceAuditComponent
@ConditionalOnProperty(
        prefix = PersistenceAuditConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = PersistenceAuditConstant.CONFIG_ENABLED,
        havingValue = "true"
)
public class LogEsPersistenceAuditHandler implements EsPersistenceAuditHandler {

    @Override
    public void handle(EsPersistenceAuditRecord record) {
        log.info(PersistenceAuditConstant.LOG_RECORD_FORMAT, record);
    }
}
