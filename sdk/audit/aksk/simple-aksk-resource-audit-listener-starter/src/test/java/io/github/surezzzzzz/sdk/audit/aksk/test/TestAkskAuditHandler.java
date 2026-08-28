package io.github.surezzzzzz.sdk.audit.aksk.test;

import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
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
public class TestAkskAuditHandler implements AkskAuditHandler {

    public final List<AkskAuditRecord> records = new ArrayList<AkskAuditRecord>();
    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(AkskAuditRecord record) {
        log.info("收到AKSK资源审计记录: subjectId={}, uri={}, requestId={}",
                record.getSubjectId(), record.getRequestUri(), record.getRequestId());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
    }
}
