package io.github.surezzzzzz.sdk.audit.license.test.cases;

import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.listener.LicenseAuditEventListener;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import io.github.surezzzzzz.sdk.license.server.event.RevocationPackageCompleted;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扩展件故障日志只保留异常类型和事件关联，不泄露异常中的令牌、载荷或嵌套原因。
 *
 * @author surezzzzzz
 */
class LicenseAuditFailureLoggingTest {

    @Test
    void handlerAndTraceFailuresDoNotLeakExceptionTextOrPreventTheNextHandler() {
        String sensitiveMarker = "isolated-sensitive-marker";
        List<LicenseAuditRecord> received = new ArrayList<LicenseAuditRecord>();
        LicenseAuditHandler failing = record -> {
            throw new IllegalStateException(sensitiveMarker, new RuntimeException(sensitiveMarker));
        };
        LicenseAuditEventListener listener = new LicenseAuditEventListener(
                Arrays.asList(failing, received::add), () -> {
                    throw new IllegalArgumentException(sensitiveMarker);
                });
        Logger logger = (Logger) LogManager.getLogger(LicenseAuditEventListener.class);
        Level previousLevel = logger.getLevel();
        List<LogEvent> messages = new ArrayList<LogEvent>();
        // 仓库测试统一使用Log4j2，直接观察日志事件，不引入另一套日志实现。
        AbstractAppender captured = new AbstractAppender("license-failure-capture", null,
                PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent entry) {
                messages.add(entry.toImmutable());
            }
        };
        captured.start();
        logger.addAppender(captured);
        // 无Spring启动的测试默认只输出ERROR；本用例需同时观察WARN，结束恢复原级别。
        logger.setLevel(Level.WARN);
        RevocationPackageCompleted event = new RevocationPackageCompleted(
                "rp-example", "operation-example", "product-example", 1L, "READY", null);
        try {
            listener.onRevocationPackageCompleted(event);
            assertThat(received).hasSize(1);
            assertThat(received.get(0).getEventId()).isEqualTo(event.getEventId());
            assertThat(received.get(0).getTraceId()).isNull();
            assertThat(messages).hasSize(2);
            assertThat(messages).allSatisfy(entry -> {
                assertThat(entry.getMessage().getFormattedMessage()).doesNotContain(sensitiveMarker);
                assertThat(entry.getThrown()).isNull();
            });
            assertThat(messages.get(0).getMessage().getFormattedMessage()).contains("IllegalArgumentException");
            assertThat(messages.get(1).getMessage().getFormattedMessage())
                    .contains(event.getEventId(), "IllegalStateException");
        } finally {
            logger.removeAppender(captured);
            logger.setLevel(previousLevel);
            captured.stop();
        }
    }
}
