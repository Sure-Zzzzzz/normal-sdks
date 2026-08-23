package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider.ElasticsearchXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Elasticsearch XFF Capture Audit Provider 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditEsProviderTest {

    @Test
    void shouldWriteFixedLogicalIndexWithEventId() {
        PersistenceEngine persistenceEngine = mock(PersistenceEngine.class);
        ElasticsearchXffCaptureAuditPersistenceProvider provider =
                new ElasticsearchXffCaptureAuditPersistenceProvider(persistenceEngine);
        XffCaptureAuditDocument document = new XffCaptureAuditDocument(
                "event-1", "2026-08-20T00:00:00.000Z", "test-service", null, null,
                "GET", "/health", Collections.<String>emptyList(), true,
                Collections.singletonList("10.0.0.1"), Collections.singletonList("10.0.0.1"),
                Collections.singletonList("10.0.0.1"), Collections.singletonList("audit.example.test"),
                Collections.singletonList("443"), Collections.singletonList("https"),
                Collections.singletonList("10.0.0.1"), Collections.<String>emptyList(),
                "10.0.0.1", "10.0.0.1", "iana-2025-10-09",
                Collections.<String, String>emptyMap(),
                io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot.disabled());

        provider.persist(document);

        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(persistenceEngine).index(captor.capture());
        IndexRequest request = captor.getValue();
        log.info("验证 Elasticsearch Provider 写入事件：index={}，id={}",
                request.getIndex(), request.getId());
        assertEquals("xff-capture-audit", request.getIndex(), "必须使用固定逻辑索引");
        assertEquals(document.getEventId(), request.getId(), "必须使用 eventId 作为 ES 文档 ID");
        assertTrue(request.getDocument() instanceof Map, "ES Provider 必须传入稳定字段名的文档 Map");
        Map<?, ?> source = (Map<?, ?>) request.getDocument();
        assertEquals(Collections.singletonList("10.0.0.1"), source.get("xRealIpList"),
                "X-Real-IP 字段名必须保持公开审计契约");
        assertTrue(source.containsKey("xForwardedHostList"), "Forwarded Host 字段必须保留");
        assertTrue(source.containsKey("xForwardedPortList"), "Forwarded Port 字段必须保留");
        assertTrue(source.containsKey("xForwardedProtoList"), "Forwarded Proto 字段必须保留");
        assertTrue(source.containsKey("requestData"), "请求数据必须保留");
        assertFalse(source.containsKey("xrealIpList"), "不得使用 Jackson 非直观属性名");
        verifyNoMoreInteractions(persistenceEngine);
    }
}
