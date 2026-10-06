package io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.impl;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.annotation.SimpleElasticsearchAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Log Elasticsearch Search 审计处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchAuditListenerComponent
@ConditionalOnProperty(
        prefix = SimpleElasticsearchAuditListenerConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = SimpleElasticsearchAuditListenerConstant.CONFIG_ENABLED,
        havingValue = "true"
)
public class LogEsAuditHandler implements EsAuditHandler {

    /**
     * 输出审计记录；仅在显式启用日志 Handler 时执行。
     */
    @Override
    public void handle(EsAuditRecord record) {
        log.info(SimpleElasticsearchAuditListenerConstant.LOG_RECORD_FORMAT, record);
    }
}
