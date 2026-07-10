package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用 persistence 审计处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@Component
public class TestEsPersistenceAuditHandler implements EsPersistenceAuditHandler {

    public final List<EsPersistenceAuditRecord> records = new ArrayList<EsPersistenceAuditRecord>();
    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(EsPersistenceAuditRecord record) {
        log.info("Received ES persistence audit record: operation={}, result={}, index={}, datasource={}, traceId={}",
                record.getOperationType(), record.getResult(), record.getIndex(), record.getDatasource(), record.getTraceId());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
    }
}
