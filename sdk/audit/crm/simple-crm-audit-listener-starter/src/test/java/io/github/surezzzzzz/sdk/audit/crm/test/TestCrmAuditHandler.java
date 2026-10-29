package io.github.surezzzzzz.sdk.audit.crm.test;

import io.github.surezzzzzz.sdk.audit.crm.handler.CrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的 CRM 审计处理器
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class TestCrmAuditHandler implements CrmAuditHandler {

    public final List<CrmAuditRecord> records = new ArrayList<CrmAuditRecord>();

    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(CrmAuditRecord record) {
        log.info("收到CRM审计记录: eventType={}，owner={}，resourceId={}，correlationId={}",
                record.getEventType(), record.getOwner(), record.getResourceId(),
                record.getCorrelationId());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
    }
}
