package io.github.surezzzzzz.sdk.audit.iam.server.test;

import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditEventFamily;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的 IAM 审计处理器
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class TestServerIamAuditHandler implements ServerIamAuditHandler {

    public final List<ServerIamAuditRecord> records = new ArrayList<>();
    public CountDownLatch latch = new CountDownLatch(1);
    private ServerIamAuditEventFamily expectedFamily = null;
    private String expectedEventType = null;

    @Override
    public void handle(ServerIamAuditRecord record) {
        log.info("收到 IAM 审计记录: family={}, eventType={}, cause={}, username={}, subjectId={}",
                record.getFamily(), record.getEventType(), record.getCause(),
                record.getUsername(), record.getSubjectId());
        if (matches(record)) {
            records.add(record);
            latch.countDown();
        }
    }

    public void reset(ServerIamAuditEventFamily expectedFamily, String expectedEventType) {
        records.clear();
        latch = new CountDownLatch(1);
        this.expectedFamily = expectedFamily;
        this.expectedEventType = expectedEventType;
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
        this.expectedFamily = null;
        this.expectedEventType = null;
    }

    private boolean matches(ServerIamAuditRecord record) {
        if (expectedFamily != null && expectedFamily != record.getFamily()) {
            return false;
        }
        return expectedEventType == null || expectedEventType.equals(record.getEventType());
    }
}
