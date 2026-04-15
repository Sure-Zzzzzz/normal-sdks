package io.github.surezzzzzz.sdk.audit.aksk.server.test;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的 Server Token 审计处理器
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class TestServerTokenAuditHandler implements ServerTokenAuditHandler {

    public final List<ServerTokenAuditRecord> records = new ArrayList<>();
    public CountDownLatch latch = new CountDownLatch(1);
    private TokenEventType expectedType = null;

    @Override
    public void handle(ServerTokenAuditRecord record) {
        log.info("Received server token audit record: eventType={}, clientId={}, userId={}",
                record.getEventType(), record.getClientId(), record.getUserId());
        if (expectedType == null || expectedType == record.getEventType()) {
            records.add(record);
            latch.countDown();
        }
    }

    public void reset(TokenEventType expectedType) {
        records.clear();
        latch = new CountDownLatch(1);
        this.expectedType = expectedType;
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
        this.expectedType = null;
    }
}

