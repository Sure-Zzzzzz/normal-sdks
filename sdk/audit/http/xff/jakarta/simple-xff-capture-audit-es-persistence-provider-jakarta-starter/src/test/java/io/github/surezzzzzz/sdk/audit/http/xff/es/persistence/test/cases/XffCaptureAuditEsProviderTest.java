package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider.ElasticsearchXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Provider 写入契约与失败传播测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditEsProviderTest {

    @Test
    void shouldWriteFixedLogicalIndexWithStableFieldNames() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        ElasticsearchXffCaptureAuditPersistenceProvider provider =
                new ElasticsearchXffCaptureAuditPersistenceProvider(engine);
        XffCaptureAuditDocument document = document();

        provider.persist(document);

        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(engine).index(captor.capture());
        IndexRequest request = captor.getValue();
        log.info("Provider 写入目标：index={}，id={}", request.getIndex(), request.getId());
        assertEquals("xff-capture-audit", request.getIndex(), "固定逻辑索引不能漂移");
        assertEquals(document.getEventId(), request.getId(), "ES 文档 ID 必须等于事件 ID");
        assertTrue(request.getDocument() instanceof Map, "公开字段应在提交前稳定投影");
        Map<?, ?> source = (Map<?, ?>) request.getDocument();
        assertEquals(Collections.singletonList("10.0.0.1"), source.get("xRealIpList"),
                "X-Real-IP 字段名必须保持契约");
        assertTrue(source.containsKey("xForwardedHostList"), "转发 Host 字段不能丢失");
        assertTrue(source.containsKey("requestData"), "请求快照不能丢失");
        assertFalse(source.containsKey("xrealIpList"), "不得生成错误的缩写属性名");
        verifyNoMoreInteractions(engine);
    }

    @Test
    void shouldPropagatePersistenceFailureToListener() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        RuntimeException failure = new RuntimeException("test-only failure");
        when(engine.index(any(IndexRequest.class))).thenThrow(failure);
        ElasticsearchXffCaptureAuditPersistenceProvider provider =
                new ElasticsearchXffCaptureAuditPersistenceProvider(engine);

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> provider.persist(document()));

        log.info("Provider 失败传播类型：{}", actual.getClass().getName());
        assertSame(failure, actual, "Provider 不得把失败伪装成成功");
        verify(engine).index(any(IndexRequest.class));
    }

    private XffCaptureAuditDocument document() {
        return new XffCaptureAuditDocument(
                "event-1", "2026-08-20T00:00:00.000Z", "test-service", null, null,
                "GET", "/health", Collections.emptyList(), true,
                Collections.singletonList("10.0.0.1"), Collections.singletonList("10.0.0.1"),
                Collections.singletonList("10.0.0.1"), Collections.singletonList("audit.example.test"),
                Collections.singletonList("443"), Collections.singletonList("https"),
                Collections.singletonList("10.0.0.1"), Collections.emptyList(),
                "10.0.0.1", "10.0.0.1", "iana-2025-10-09",
                Collections.emptyMap(), RequestDataSnapshot.disabled());
    }
}
