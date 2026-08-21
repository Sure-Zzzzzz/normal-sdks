package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.factory.XffCaptureAuditDocumentFactory;
import io.github.surezzzzzz.sdk.audit.http.xff.listener.XffCaptureAuditEventListener;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * XFF Capture 审计 Listener 单元测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(OutputCaptureExtension.class)
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
    void shouldLogFullProviderThrowableWithoutBlockingFollowingProvider(CapturedOutput output) {
        XffCaptureAuditPersistenceProvider failedProvider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditPersistenceProvider followingProvider = mock(XffCaptureAuditPersistenceProvider.class);
        RuntimeException failure = new RuntimeException("provider-failure-marker");
        doThrow(failure).when(failedProvider).persist(any(XffCaptureAuditDocument.class));
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-provider-log", true);
        XffCaptureAuditDocument document = document("event-provider-log");
        when(factory.create(event)).thenReturn(document);
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Arrays.asList(failedProvider, followingProvider), factory, Runnable::run);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event));

        log.info("Provider 异常堆栈输出长度：{}", output.getOut().length());
        assertTrue(output.getOut().contains("provider-failure-marker"), "Provider 异常消息必须进入完整堆栈日志");
        verify(followingProvider).persist(document);
    }

    @Test
    void shouldContinueBroadcastWhenOneProviderFails() {
        XffCaptureAuditPersistenceProvider failedProvider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditPersistenceProvider followingProvider = mock(XffCaptureAuditPersistenceProvider.class);
        doThrow(new RuntimeException("provider-failure-without-log-assertion")).when(failedProvider)
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
    void shouldLogFullConversionThrowable(CapturedOutput output) {
        XffCaptureAuditPersistenceProvider provider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-convert-log", true);
        when(factory.create(event)).thenThrow(new RuntimeException("conversion-failure-marker"));
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Collections.singletonList(provider), factory, Runnable::run);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event));

        log.info("转换异常堆栈输出长度：{}", output.getOut().length());
        assertTrue(output.getOut().contains("conversion-failure-marker"), "转换异常必须进入完整堆栈日志");
        verifyNoInteractions(provider);
    }

    @Test
    void shouldIsolateDocumentConversionFailure() {
        XffCaptureAuditPersistenceProvider provider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-convert-failure", true);
        when(factory.create(event)).thenThrow(new RuntimeException("provider-failure-without-log-assertion"));
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Collections.singletonList(provider), factory, Runnable::run);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event),
                "文档转换失败不能传播到 Capture 事件发布线程");

        log.info("验证转换失败不调用 Provider：eventId={}", event.getEventId());
        verifyNoInteractions(provider);
    }

    @Test
    void shouldLogFullQueueRejectionThrowable(CapturedOutput output) {
        XffCaptureAuditPersistenceProvider provider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-rejected-log", true);
        when(factory.create(event)).thenReturn(document("event-rejected-log"));
        Executor rejectedExecutor = command -> {
            throw new RejectedExecutionException("queue-rejection-marker");
        };
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Collections.singletonList(provider), factory, rejectedExecutor);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event));

        log.info("队列异常堆栈输出长度：{}", output.getOut().length());
        assertTrue(output.getOut().contains("queue-rejection-marker"), "队列拒绝异常必须进入完整堆栈日志");
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

    @Test
    void shouldLogFullTaskSubmissionThrowable(CapturedOutput output) {
        XffCaptureAuditPersistenceProvider provider = mock(XffCaptureAuditPersistenceProvider.class);
        XffCaptureAuditDocumentFactory factory = mock(XffCaptureAuditDocumentFactory.class);
        XffCaptureEvent event = event("event-submit-log", true);
        when(factory.create(event)).thenReturn(document("event-submit-log"));
        Executor failedExecutor = command -> {
            throw new RuntimeException("submission-failure-marker");
        };
        XffCaptureAuditEventListener listener = new XffCaptureAuditEventListener(
                Collections.singletonList(provider), factory, failedExecutor);

        assertDoesNotThrow(() -> listener.onXffCaptureEvent(event));

        log.info("提交异常堆栈输出长度：{}", output.getOut().length());
        assertTrue(output.getOut().contains("submission-failure-marker"), "提交异常必须进入完整堆栈日志");
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
                "GET", "/test", "10.0.0.10", new XffCaptureSnapshot(chain, context), requestData());
    }

    private RequestDataSnapshot requestData() {
        RequestParameterSnapshot query = new RequestParameterSnapshot(
                RequestDataCaptureStatus.CAPTURED,
                Collections.singletonMap("query", Collections.singletonList("query-value")));
        RequestParameterSnapshot form = new RequestParameterSnapshot(
                RequestDataCaptureStatus.CAPTURED,
                Collections.singletonMap("form", Collections.singletonList("form-value")));
        RequestBodySnapshot body = new RequestBodySnapshot(
                RequestBodyCaptureStatus.CAPTURED, "application/json", 16L, 16L,
                "{\"body\":true}");
        return new RequestDataSnapshot(query, form, body);
    }
}
