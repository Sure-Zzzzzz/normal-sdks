package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerClientLifecycleAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.listener.ServerClientLifecycleAuditEventListener;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerClientLifecycleAuditRecord;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEventType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AKU 生命周期审计监听器：按业务提供 Handler 装配、事件字段映射与单 Handler 异常隔离。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = ServerClientLifecycleAuditListenerTest.LifecycleConfiguration.class)
class ServerClientLifecycleAuditListenerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ServerClientLifecycleAuditEventListener listener;

    @Autowired
    private RecordingLifecycleAuditHandler recordingHandler;

    @Autowired
    private ThrowingLifecycleAuditHandler throwingHandler;

    @org.junit.jupiter.api.BeforeEach
    void resetRecordingHandler() {
        // Handler 为单例 Bean，记录列表跨用例共享；每用例前清空保证计数独立
        recordingHandler.records.clear();
    }

    @Test
    void shouldRegisterSingleListenerWhenHandlersPresent() {
        // 默认日志 Handler（matchIfMissing=true）+ 两个业务测试 Handler 共存，监听器只装配一个
        assertEquals(1, applicationContext.getBeansOfType(ServerClientLifecycleAuditEventListener.class).size());
    }

    @Test
    void shouldMapAllDesensitizedEventFieldsIntoRecord() {
        AkskClientLifecycleEvent event = new AkskClientLifecycleEvent(this,
                AkskClientLifecycleEventType.SECRET_ROTATED, "AKU123", "local-iam",
                "2388256181993590", 42L, 7L);

        listener.onClientLifecycleEvent(event);

        assertEquals(1, recordingHandler.records.size());
        ServerClientLifecycleAuditRecord record = recordingHandler.records.get(0);
        assertEquals(AkskClientLifecycleEventType.SECRET_ROTATED, record.getEventType());
        assertEquals("AKU123", record.getClientId());
        assertEquals("local-iam", record.getOwnerSourceId());
        assertEquals("2388256181993590", record.getOwnerSubjectId());
        assertEquals(Long.valueOf(42L), record.getTargetApplicationId());
        assertEquals(Long.valueOf(7L), record.getLifecycleVersion());
        Instant eventTime = record.getEventTime();
        assertTrue(eventTime != null && !eventTime.isBefore(Instant.now().minusSeconds(60)));
    }

    @Test
    void shouldIsolateSingleHandlerFailureAndContinueOthers() {
        AkskClientLifecycleEvent event = new AkskClientLifecycleEvent(this,
                AkskClientLifecycleEventType.TERMINATED, "AKU456", "local-iam",
                "2388256181993590", 42L, 8L);

        listener.onClientLifecycleEvent(event);

        assertEquals(1, recordingHandler.records.size(), "前置异常 Handler 不得阻断后续 Handler");
    }

    static class RecordingLifecycleAuditHandler implements ServerClientLifecycleAuditHandler {

        final List<ServerClientLifecycleAuditRecord> records = new ArrayList<>();

        @Override
        public void handle(ServerClientLifecycleAuditRecord record) {
            records.add(record);
        }
    }

    static class ThrowingLifecycleAuditHandler implements ServerClientLifecycleAuditHandler {

        @Override
        public void handle(ServerClientLifecycleAuditRecord record) {
            throw new IllegalStateException("审计落库暂不可用（测试注入）");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class LifecycleConfiguration {

        @Bean
        RecordingLifecycleAuditHandler recordingLifecycleAuditHandler() {
            return new RecordingLifecycleAuditHandler();
        }

        @Bean
        ThrowingLifecycleAuditHandler throwingLifecycleAuditHandler() {
            return new ThrowingLifecycleAuditHandler();
        }
    }
}
