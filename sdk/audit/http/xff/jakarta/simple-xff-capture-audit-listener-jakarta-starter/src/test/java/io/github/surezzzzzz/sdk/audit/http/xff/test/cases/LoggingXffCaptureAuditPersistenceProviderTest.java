package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.LoggingXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.test.SimpleXffCaptureAuditListenerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XFF Capture 默认日志 Provider 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureAuditListenerTestApplication.class,
        properties = {
                SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX + ".enable=true",
                "spring.application.name=xff-audit-logging-test"
        })
@ExtendWith(OutputCaptureExtension.class)
class LoggingXffCaptureAuditPersistenceProviderTest {

    @Autowired
    @Qualifier(SimpleXffCaptureAuditListenerConstant.LOGGING_PROVIDER_BEAN_NAME)
    private LoggingXffCaptureAuditPersistenceProvider provider;

    @Test
    void shouldLogControlledSummaryWithoutSensitiveFields(CapturedOutput output) {
        XffCaptureAuditDocument document = new XffCaptureAuditDocument(
                "event-safe-1", "2026-08-20T00:00:00.000Z", "test-service",
                "request-safe-1", "trace-sensitive-1", "GET", "/uri-sensitive-1",
                Collections.singletonList("host-sensitive-1"), true,
                Collections.singletonList("xff-sensitive-1"),
                Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), "remote-sensitive-1", null,
                "iana-2025-10-09", Collections.singletonMap("clientId", "extension-sensitive-1"));
        int outputLengthBeforePersist = output.getOut().length();

        provider.persist(document);

        String logOutput = output.getOut().substring(outputLengthBeforePersist);
        log.info("默认日志 Provider 输出长度：{}", logOutput.length());
        assertTrue(logOutput.contains("XFF 审计事件已投影"), "日志应包含投影提示");
        assertTrue(logOutput.contains("eventId=[event-safe-1]"), "日志应包含 eventId");
        assertTrue(logOutput.contains("applicationName=[test-service]"), "日志应包含应用名称");
        assertTrue(logOutput.contains("requestId=[request-safe-1]"), "日志应包含 requestId");
        assertTrue(logOutput.contains("xffPresent=[true]"), "日志应包含 XFF 存在标识");
        assertTrue(logOutput.contains("xffIpCount=[1]"), "日志应包含 XFF IP 数量");
        assertTrue(logOutput.contains("publicIpCount=[1]"), "日志应包含公网 IP 数量");
        assertFalse(logOutput.contains("trace-sensitive-1"), "日志不能包含 traceId");
        assertFalse(logOutput.contains("/uri-sensitive-1"), "日志不能包含 URI");
        assertFalse(logOutput.contains("host-sensitive-1"), "日志不能包含 Host");
        assertFalse(logOutput.contains("xff-sensitive-1"), "日志不能包含完整原始 XFF");
        assertFalse(logOutput.contains("remote-sensitive-1"), "日志不能包含原始远端地址");
        assertFalse(logOutput.contains("clientId"), "日志不能包含业务扩展键");
        assertFalse(logOutput.contains("extension-sensitive-1"), "日志不能包含业务扩展值");
    }

}


