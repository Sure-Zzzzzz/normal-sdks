package io.github.surezzzzzz.sdk.audit.limiter.test.support;

import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的限流审计处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@Component
public class TestSmartRedisLimiterAuditHandler implements SmartRedisLimiterAuditHandler {

    public final List<SmartRedisLimiterRecord> records = new ArrayList<>();
    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(SmartRedisLimiterRecord record) {
        log.info("Received audit record: passed={}, source={}, algorithm={}, limitKey={}",
                record.isPassed(), record.getSource(), record.getAlgorithm(), record.getLimitKey());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
    }
}
