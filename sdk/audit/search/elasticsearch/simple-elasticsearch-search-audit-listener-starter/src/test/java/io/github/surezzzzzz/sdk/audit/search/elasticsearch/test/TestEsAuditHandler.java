package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的审计处理器
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class TestEsAuditHandler implements EsAuditHandler {

    public final List<EsAuditRecord> records = new ArrayList<EsAuditRecord>();
    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(EsAuditRecord record) {
        log.info("Received ES audit record: user={}, index={}, datasource={}, total={}, traceId={}",
                record.getUsername(), record.getIndexAlias(), record.getDatasource(),
                record.getTotal(), record.getTraceId());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        reset(1);
    }

    public void reset(int count) {
        records.clear();
        latch = new CountDownLatch(count);
    }
}
