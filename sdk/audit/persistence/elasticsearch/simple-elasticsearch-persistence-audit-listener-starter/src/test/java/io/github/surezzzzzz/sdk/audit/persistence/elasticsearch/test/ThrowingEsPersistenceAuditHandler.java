package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 测试用异常 persistence 审计处理器
 *
 * @author surezzzzzz
 */
@Component
@ConditionalOnProperty(prefix = "test.es.persistence.audit", name = "throwing-handler", havingValue = "true")
public class ThrowingEsPersistenceAuditHandler implements EsPersistenceAuditHandler {

    @Override
    public void handle(EsPersistenceAuditRecord record) {
        throw new IllegalStateException("test handler failed");
    }
}
