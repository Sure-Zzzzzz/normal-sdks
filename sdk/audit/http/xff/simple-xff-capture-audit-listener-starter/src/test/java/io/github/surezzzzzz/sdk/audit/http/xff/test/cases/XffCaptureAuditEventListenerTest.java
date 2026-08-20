package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.factory.XffCaptureAuditDocumentFactory;
import io.github.surezzzzzz.sdk.audit.http.xff.listener.XffCaptureAuditEventListener;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.model.ForwardedContext;
import io.github.surezzzzzz.sdk.http.xff.core.model.HeaderValueSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffCaptureSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * XFF Capture 审计 Listener 单元测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditEventListenerTest {

    @Test
    void shouldBroadcastSameDocumentToAllProviders() {
        XffCaptureAuditPersistenceProvider firstProvider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditPersistenceProvider secondProvider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureAuditDocument document = document("event-1");
        XffCaptureEvent event = event("event-1", true);
        when(factory.create(event)).thenReturn(document);
        Executor executor = mock(Executor.class);
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Arrays.asList(firstProvider, secondProvider), factory, executor);

        listener.onXffCaptureEvent(event);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(taskCaptor.capture());
        verifyNoInteractions(firstProvider, secondProvider);

        taskCaptor.getValue().run();

        ArgumentCaptor<XffCaptureAuditDocument> firstCaptor =
                ArgumentCaptor.forClass(XffCaptureAuditDocument.class);
        ArgumentCaptor<XffCaptureAuditDocument> secondCaptor =
                ArgumentCaptor.forClass(XffCaptureAuditDocument.class);
        InOrder providerOrder = inOrder(firstProvider, secondProvider);
        providerOrder.verify(firstProvider).persist(firstCaptor.capture());
        providerOrder.verify(secondProvider).persist(secondCaptor.capture());
        log.info("Provider 广播文档：eventId={}，sameInstance={}", document.getEventId(),
                firstCaptor.getValue() == secondCaptor.getValue());
        assertSame(document, firstCaptor.getValue(), "第一个 Provider 应收到同一文档");
        assertSame(document, secondCaptor.getValue(), "第二个 Provider 应收到同一文档");
        verifyNoMoreInteractions(firstProvider, secondProvider);
    }

    @Test
    void shouldContinueBroadcastWhenOneProviderFails() {
        XffCaptureAuditPersistenceProvider failedProvider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditPersistenceProvider followingProvider = mock(XffCaptureAuditPersistenceProvider.class);
        doThrow(new RuntimeException("sensitive-test-value")).when(failedProvider)
                .persist(any(XffCaptureAuditDocument.class));
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-provider-failure", true);
        XffCaptureAuditDocument document = document("event-provider-failure");
        when(factory.create(event)).thenReturn(document);
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Arrays.asList(failedProvider, followingProvider), factory, Runnable::run);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event),
                "Provider 失败不能传播到 Capture 事件发布线程");

        log.info("验证 Provider 失败后仍执行后续 Provider：eventId={}", document.getEventId());
        verify(failedProvider).persist(document);
        verify(followingProvider).persist(document);
    }

    @Test
    void shouldIsolateDocumentConversionFailure() {
        XffCaptureAuditPersistenceProvider provider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-convert-failure", true);
        when(factory.create(event)).thenThrow(new RuntimeException("sensitive-test-value"));
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Collections.singletonList(provider), factory, Runnable::run);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event),
                "文档转换失败不能传播到 Capture 事件发布线程");

        log.info("验证转换失败不调用 Provider：eventId={}", event.getEventId());
        verifyNoInteractions(provider);
    }

    @Test
    void shouldIsolateQueueRejection() {
        XffCaptureAuditPersistenceProvider provider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-rejected", true);
        when(factory.create(event)).thenReturn(document("event-rejected"));
        Executor rejectedExecutor = command -> {
            throw new RejectedExecutionException("test rejected");
        };
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Collections.singletonList(provider), factory, rejectedExecutor);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event),
                "队列拒绝不能传播到 Capture 事件发布线程");

        log.info("验证队列拒绝不调用 Provider：eventId={}", event.getEventId());
        verifyNoInteractions(provider);
    }

    private XffCaptureAuditDocument document(String eventId) {
        return new XffCaptureAuditDocument(eventId, "2026-08-19T00:00:00.000Z", "test-service",
                null, null, "GET", "/test", Collections.singletonList("test.example"),
                true, Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), "10.0.0.10", "10.0.0.10",
                "iana-2025-10-09");
    }

    private XffCaptureEvent event(String eventId, boolean xffPresent) {
        XffChain chain = xffPresent
                ? new XffChain(true, Collections.singletonList("8.8.8.8, 10.0.0.1"),
                Arrays.asList("8.8.8.8", "10.0.0.1"))
                : new XffChain(false, Collections.<String>emptyList(), Collections.<String>emptyList());
        HeaderValueSnapshot absent = new HeaderValueSnapshot(false, Collections.<String>emptyList());
        ForwardedContext context = new ForwardedContext(absent, absent, absent, absent, absent);
        return new XffCaptureEvent(eventId, Instant.parse("2026-08-19T00:00:00Z"),
                "GET", "/test", "10.0.0.10", new XffCaptureSnapshot(chain, context));
    }
}
