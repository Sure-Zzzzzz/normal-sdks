package io.github.surezzzzzz.sdk.audit.license.test;

import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的 License 审计处理器
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class TestLicenseAuditHandler implements LicenseAuditHandler {

    public final List<LicenseAuditRecord> records = new ArrayList<LicenseAuditRecord>();
    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(LicenseAuditRecord record) {
        log.info("收到License审计记录: eventType={}, grantId={}, principalId={}, requestId={}",
                record.getEventType(), record.getGrantId(), record.getPrincipalId(), record.getRequestId());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
    }
}
