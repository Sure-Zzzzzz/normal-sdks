package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.test.SimpleXffCaptureAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF Capture Audit Listener 真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=xff-audit-listener-test-service",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
                "io.github.surezzzzzz.sdk.http.xff.capture.enable=true",
                "io.github.surezzzzzz.sdk.http.xff.capture.request-data.query-parameters.enabled=true",
                "io.github.surezzzzzz.sdk.http.xff.capture.request-data.body.enabled=true",
                "io.github.surezzzzzz.sdk.http.xff.capture.request-data.body.max-bytes=64",
                "io.github.surezzzzzz.sdk.http.xff.capture.request-data.body.allowed-content-types[0]=application/json",
                "io.github.surezzzzzz.sdk.http.xff.capture.request-data.whitelist[0].method=POST",
                "io.github.surezzzzzz.sdk.http.xff.capture.request-data.whitelist[0].path-pattern=/listener-http/audit",
                "io.github.surezzzzzz.sdk.audit.http.xff.capture.listener.enable=true"
        })
@Import(XffCaptureAuditListenerIntegrationTest.ListenerHttpTestConfiguration.class)
class XffCaptureAuditListenerIntegrationTest {

    private static final long AUDIT_DOCUMENT_TIMEOUT_SECONDS = 5L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RecordingAuditProvider recordingProvider;

    @Autowired
    private RecordingCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        recordingProvider.clear();
        eventListener.clear();
    }

    @Test
    void shouldProjectCaptureEventRequestDataToAsyncAuditDocument() throws InterruptedException {
        String requestBody = "{\"message\":\"audit-body\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Forwarded-For", "8.8.8.8, 10.0.0.1");

        ResponseEntity<String> response = restTemplate.exchange(
                "/listener-http/audit?tag=one&tag=two", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        boolean documentReceived = recordingProvider.awaitDocument();
        XffCaptureEvent event = eventListener.firstEvent();
        XffCaptureAuditDocument document = recordingProvider.firstDocument();

        log.info("Listener 真实 HTTP 闭环：status={}，eventId={}，documentReceived={}，"
                        + "queryStatus={}，bodyStatus={}，xffIpCount={}",
                response.getStatusCodeValue(), event == null ? null : event.getEventId(), documentReceived,
                document == null ? null : document.getRequestData().getQueryParameters().getStatus(),
                document == null ? null : document.getRequestData().getBody().getStatus(),
                document == null ? 0 : document.getXffIpList().size());
        assertEquals(HttpStatus.OK, response.getStatusCode(), "真实 HTTP 请求必须成功");
        assertEquals(requestBody, response.getBody(), "Capture 不得破坏 Controller 接收的完整 Body");
        assertTrue(documentReceived, "Listener 必须在截止时间内异步投影审计文档");
        assertNotNull(event, "Capture Filter 必须发布 XFF Capture Event");
        assertNotNull(document, "Provider 必须收到审计文档");
        assertEquals(event.getEventId(), document.getEventId(), "Document 必须来自当前 Capture Event");
        assertEquals("POST", document.getRequestMethod(), "请求方法必须保留");
        assertEquals("/listener-http/audit", document.getRequestUri(), "URI 不得包含 Query String");
        assertEquals(Collections.singletonList("8.8.8.8"), document.getPublicIpList(),
                "公网 XFF 投影必须保留");
        assertSame(event.getRequestData(), document.getRequestData(),
                "Document 必须直接持有 Event 的不可变请求数据快照");
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                document.getRequestData().getQueryParameters().getStatus(),
                "Query 快照必须进入审计文档");
        assertEquals(2, document.getRequestData().getQueryParameters().getValues().get("tag").size(),
                "Query 多值必须完整投影");
        assertEquals(RequestBodyCaptureStatus.CAPTURED, document.getRequestData().getBody().getStatus(),
                "Body 快照必须进入审计文档");
        assertEquals(requestBody, document.getRequestData().getBody().getText(),
                "受限 Body 文本必须原样投影");
    }

    @TestConfiguration
    static class ListenerHttpTestConfiguration {

        @Bean
        ListenerHttpTestController listenerHttpTestController() {
            return new ListenerHttpTestController();
        }

        @Bean
        RecordingAuditProvider recordingAuditProvider() {
            return new RecordingAuditProvider();
        }

        @Bean
        RecordingCaptureEventListener recordingCaptureEventListener() {
            return new RecordingCaptureEventListener();
        }
    }

    @RestController
    static class ListenerHttpTestController {

        @PostMapping("/listener-http/audit")
        String audit(@RequestBody String requestBody) {
            return requestBody;
        }
    }

    static class RecordingAuditProvider implements XffCaptureAuditPersistenceProvider {

        private final List<XffCaptureAuditDocument> documents =
                Collections.synchronizedList(new ArrayList<XffCaptureAuditDocument>());
        private volatile CountDownLatch documentLatch = new CountDownLatch(1);

        @Override
        public void persist(XffCaptureAuditDocument document) {
            documents.add(document);
            documentLatch.countDown();
        }

        void clear() {
            documents.clear();
            documentLatch = new CountDownLatch(1);
        }

        boolean awaitDocument() throws InterruptedException {
            return documentLatch.await(AUDIT_DOCUMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        XffCaptureAuditDocument firstDocument() {
            synchronized (documents) {
                return documents.isEmpty() ? null : documents.get(0);
            }
        }
    }

    static class RecordingCaptureEventListener {

        private final List<XffCaptureEvent> events =
                Collections.synchronizedList(new ArrayList<XffCaptureEvent>());

        @EventListener
        public void onXffCaptureEvent(XffCaptureEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        XffCaptureEvent firstEvent() {
            synchronized (events) {
                return events.isEmpty() ? null : events.get(0);
            }
        }
    }
}
