package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties;
import io.github.surezzzzzz.sdk.ticket.server.service.NotificationDispatcher;
import io.github.surezzzzzz.sdk.ticket.server.service.SlaScanService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketSlaScanTask;
import io.github.surezzzzzz.sdk.ticket.server.spi.NotificationProvider;
import io.github.surezzzzzz.sdk.ticket.server.test.support.TicketLogCapture;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 外部异常消息不得进入工单运行日志。
 */
@Slf4j
class TicketFailureLogTest {

    @Test
    void notificationRetryLogsOnlyFailureClassAndAttemptCount() {
        log.debug("验证通知重试失败的日志边界");
        AtomicInteger calls = new AtomicInteger();
        NotificationProvider provider = (subjectIds, title, content) -> {
            calls.incrementAndGet();
            throw new RuntimeException("secret=private-notification-value");
        };
        SimpleTicketServerProperties properties = new SimpleTicketServerProperties();
        properties.getNotification().setEnabled(true);
        properties.getNotification().setRetryTimes(2);

        try (TicketLogCapture capture = new TicketLogCapture(NotificationDispatcher.class, Level.WARN)) {
            new NotificationDispatcher(provider, properties).dispatch(Collections.singletonList("person-1"),
                    "private-ticket-title", "private-ticket-content");

            assertEquals(2, calls.get());
            List<String> messages = capture.events().stream()
                    .map(event -> event.getMessage().getFormattedMessage()).collect(Collectors.toList());
            assertEquals(3, messages.size());
            assertEquals(Level.WARN, capture.events().get(0).getLevel());
            assertEquals(Level.ERROR, capture.events().get(2).getLevel());
            assertTrue(messages.toString().contains("第 1/2 次"));
            assertTrue(messages.toString().contains("已重试 2 次"));
            assertFalse(messages.toString().contains("private-notification-value"));
            assertFalse(messages.toString().contains("private-ticket-title"));
            assertFalse(messages.toString().contains("private-ticket-content"));
            assertTrue(capture.events().stream().noneMatch(event -> event.getThrown() != null));
        }
    }

    @Test
    void slaFailureLogsClassWithoutExceptionMessage() {
        log.debug("验证 SLA 扫描失败的日志边界");
        SlaScanService scanService = mock(SlaScanService.class);
        when(scanService.scanOnce()).thenThrow(new RuntimeException("token=private-sla-value"));

        try (TicketLogCapture capture = new TicketLogCapture(TicketSlaScanTask.class, Level.WARN)) {
            new TicketSlaScanTask(scanService).scan();

            assertEquals(1, capture.events().size());
            assertEquals(Level.WARN, capture.events().get(0).getLevel());
            assertTrue(capture.events().get(0).getMessage().getFormattedMessage().contains("SLA 扫描轮失败"));
            assertFalse(capture.events().get(0).getMessage().getFormattedMessage().contains("private-sla-value"));
            assertNull(capture.events().get(0).getThrown());
        }
    }
}
