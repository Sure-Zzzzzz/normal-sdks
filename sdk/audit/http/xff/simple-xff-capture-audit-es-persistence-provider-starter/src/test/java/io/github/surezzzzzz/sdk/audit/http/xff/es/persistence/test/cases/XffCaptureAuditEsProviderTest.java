package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider.ElasticsearchXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                "GET", "/health", Collections.<String>emptyList(), false,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), "10.0.0.1", "10.0.0.1", "iana-2025-10-09");

        provider.persist(document);

        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(persistenceEngine).index(captor.capture());
        IndexRequest request = captor.getValue();
        log.info("验证 Elasticsearch Provider 写入事件：index={}，id={}",
                request.getIndex(), request.getId());
        assertEquals("xff-capture-audit", request.getIndex(), "必须使用固定逻辑索引");
        assertEquals(document.getEventId(), request.getId(), "必须使用 eventId 作为 ES 文档 ID");
        assertSame(document, request.getDocument(), "必须使用同一份不可变审计文档");
        verifyNoMoreInteractions(persistenceEngine);
    }
}
