package io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.impl;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.annotation.SimpleElasticsearchAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Log ES Audit Handler
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchAuditListenerComponent
@ConditionalOnProperty(
        prefix = SimpleElasticsearchAuditListenerConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true"
)
public class LogEsAuditHandler implements EsAuditHandler {

    @Override
    public void handle(EsAuditRecord record) {
        log.info("ES_AUDIT - {}", record);
    }
}
