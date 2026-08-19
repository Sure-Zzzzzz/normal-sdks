package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffCaptureSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
import io.github.surezzzzzz.sdk.http.xff.service.DefaultXffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import io.github.surezzzzzz.sdk.http.xff.test.support.TestXffCaptureEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * XFF 采集服务测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class)
class XffCaptureServiceTest {

    @Autowired
    private XffCaptureService xffCaptureService;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldReturnAbsentChainWhenHeaderMissing() {
        MockHttpServletRequest request = request();

        XffChain result = xffCaptureService.capture(request);

        log.info("Header 缺失采集结果：{}", result);
        assertFalse(result.isPresent(), "Header 缺失时 present 应为 false");
        assertEquals(Collections.emptyList(), result.getRawHeaderList(), "原始值列表应为空");
        assertEquals(Collections.emptyList(), result.getRawList(), "XFF 链应为空");
        assertEquals(1, eventListener.snapshot().size(), "Header 缺失时仍应发布一次事实事件");
    }

    @Test
    void shouldPreserveMultipleHeaderValuesAndEmptyElements() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", " 192.0.2.10,\tunknown, ,10.0.0.8,");
        request.addHeader("X-Forwarded-For", "2001:db8::1");

        XffChain result = xffCaptureService.capture(request);

        List<String> expected = Arrays.asList("192.0.2.10", "unknown", "", "10.0.0.8", "", "2001:db8::1");
        log.info("多 Header XFF 链：{}", result.getRawList());
        assertTrue(result.isPresent(), "Header 存在时 present 应为 true");
        assertEquals(2, result.getRawHeaderList().size(), "应保留两个原始 Header 值");
        assertEquals(expected, result.getRawList(), "应保持顺序、非法值和空元素");
    }

    @Test
    void shouldPublishOnlyOnceForSameRequest() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        XffChain first = xffCaptureService.capture(request);
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        XffChain second = xffCaptureService.capture(request);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("同一请求事件数量：{}", events.size());
        assertSame(first, second, "同一请求应返回同一快照实例");
        assertEquals(Collections.singletonList("203.0.113.10"), second.getRawList(), "后续修改 Header 不应改变快照");
        assertEquals(1, events.size(), "同一请求最多发布一次事件");
    }

    @Test
    void shouldPreserveEmptyHeaderAndInternalWhitespace() {
        MockHttpServletRequest emptyRequest = request();
        emptyRequest.addHeader("X-Forwarded-For", "");
        XffChain emptyChain = xffCaptureService.capture(emptyRequest);

        MockHttpServletRequest whitespaceRequest = request();
        whitespaceRequest.addHeader("X-Forwarded-For", "  value with space  ,\tsecond\t");
        XffChain whitespaceChain = xffCaptureService.capture(whitespaceRequest);

        log.info("空 Header 链：{}，内部空白链：{}", emptyChain.getRawList(), whitespaceChain.getRawList());
        assertTrue(emptyChain.isPresent(), "空 Header 仍表示 Header 存在");
        assertEquals(Collections.singletonList(""), emptyChain.getRawList(), "空 Header 应保留一个空元素");
        assertEquals(Arrays.asList("value with space", "second"), whitespaceChain.getRawList(),
                "只应移除元素两侧 SP/HTAB，内部空白必须保留");
    }

    @Test
    void shouldOverwriteForeignRequestAttributeSafely() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "192.0.2.1");
        request.setAttribute(SimpleXffCaptureConstant.REQUEST_ATTRIBUTE_CAPTURE_SNAPSHOT, "foreign-value");

        XffChain result = xffCaptureService.capture(request);

        log.info("错误类型 request attribute 已安全覆盖：{}", result.getRawList());
        assertEquals(Collections.singletonList("192.0.2.1"), result.getRawList(), "应重新采集完整 XFF");
        Object snapshot = request.getAttribute(SimpleXffCaptureConstant.REQUEST_ATTRIBUTE_CAPTURE_SNAPSHOT);
        assertTrue(snapshot instanceof XffCaptureSnapshot, "应以完整不可变快照覆盖错误类型 attribute");
        assertSame(result, ((XffCaptureSnapshot) snapshot).getXffChain(),
                "完整快照应持有本次返回的 XFF 链");
        assertEquals(1, eventListener.snapshot().size(), "覆盖后应只发布一次事件");
    }

    @Test
    void shouldCaptureFixedForwardedContextWithoutMergingIntoXff() {
        MockHttpServletRequest request = request();
        request.addHeader("Host", "service.example.test");
        request.addHeader("X-Real-IP", "198.51.100.1");
        request.addHeader("X-Forwarded-Host", "gateway.example.test,second.example.test");
        request.addHeader("X-Forwarded-Port", "443");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Proto", "http");
        request.addHeader("Forwarded", "for=203.0.113.1");
        request.setRemoteAddr("10.0.0.10");

        XffChain result = xffCaptureService.capture(request);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("固定转发上下文采集：present={}，applicationRawRemoteAddress={}",
                result.isPresent(), event.getApplicationRawRemoteAddress());
        assertFalse(result.isPresent(), "没有 XFF 时不能用 X-Real-IP 或 Forwarded 回填 XFF 链");
        assertTrue(result.getRawList().isEmpty(), "remoteAddr 不能追加进 XFF 链");
        assertEquals("10.0.0.10", event.getApplicationRawRemoteAddress(), "应用可见远端地址应作为独立事件事实保存");
        assertEquals(Collections.singletonList("service.example.test"),
                event.getForwardedContext().getHost().getRawValueList(), "应原样采集 Host");
        assertEquals(Collections.singletonList("198.51.100.1"),
                event.getForwardedContext().getXRealIp().getRawValueList(), "应原样采集 X-Real-IP");
        assertEquals(Collections.singletonList("gateway.example.test,second.example.test"),
                event.getForwardedContext().getXForwardedHost().getRawValueList(),
                "应原样采集 X-Forwarded-Host，不按逗号拆分");
        assertEquals(Collections.singletonList("443"),
                event.getForwardedContext().getXForwardedPort().getRawValueList(),
                "应原样采集 X-Forwarded-Port");
        assertEquals(Arrays.asList("https", "http"),
                event.getForwardedContext().getXForwardedProto().getRawValueList(),
                "应按容器顺序原样采集多个 X-Forwarded-Proto 值");
        assertFalse(event.getForwardedContext().getXRealIp().toString().contains("198.51.100.1"),
                "Header 快照 toString 不应泄漏原始值");
    }

    @Test
    void shouldCaptureHeaderNamesCaseInsensitively() {
        MockHttpServletRequest request = request();
        request.addHeader("x-forwarded-for", "192.0.2.1");
        request.addHeader("x-real-ip", "198.51.100.1");
        request.addHeader("x-forwarded-proto", "https");

        XffChain chain = xffCaptureService.capture(request);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("小写 Header 采集：xff={}，xRealIp={}，proto={}",
                chain.getRawList(),
                event.getForwardedContext().getXRealIp().getRawValueList(),
                event.getForwardedContext().getXForwardedProto().getRawValueList());
        assertEquals(Collections.singletonList("192.0.2.1"), chain.getRawList(),
                "小写 XFF Header 应被采集");
        assertEquals(Collections.singletonList("198.51.100.1"),
                event.getForwardedContext().getXRealIp().getRawValueList(),
                "小写 X-Real-IP Header 应被采集");
        assertEquals(Collections.singletonList("https"),
                event.getForwardedContext().getXForwardedProto().getRawValueList(),
                "小写 X-Forwarded-Proto Header 应被采集");
    }

    @Test
    void shouldFallbackForCaseSensitiveNonStandardRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class, invocation -> {
            if ("getHeaders".equals(invocation.getMethod().getName())) {
                return Collections.enumeration(Collections.<String>emptyList());
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(
                Collections.singletonList("x-FoRwArDeD-fOr")));
        when(request.getHeaders("x-FoRwArDeD-fOr")).thenReturn(Collections.enumeration(
                Arrays.asList("192.0.2.1", "198.51.100.1")));
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/case-sensitive");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        XffChain chain = xffCaptureService.capture(request);

        log.info("非标准大小写敏感 request 采集链：{}", chain.getRawList());
        assertEquals(Arrays.asList("192.0.2.1", "198.51.100.1"), chain.getRawList(),
                "标准名称读不到时应按 equalsIgnoreCase 兼容实际 Header 名");
        verify(request, times(1)).getHeaders("x-FoRwArDeD-fOr");
    }

    @Test
    void shouldGenerateDifferentEventIdsForDifferentRequests() {
        MockHttpServletRequest firstRequest = request();
        MockHttpServletRequest secondRequest = request();
        xffCaptureService.capture(firstRequest);
        xffCaptureService.capture(secondRequest);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("不同请求事件 ID：first={}，second={}", events.get(0).getEventId(), events.get(1).getEventId());
        assertEquals(2, events.size(), "两个请求应各自发布一个事件");
        assertNotEquals(events.get(0).getEventId(), events.get(1).getEventId(), "不同请求事件 ID 必须不同");
    }

    @Test
    void shouldReturnImmutableLists() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "192.0.2.1");
        XffChain result = xffCaptureService.capture(request);

        log.info("验证 XFF 列表不可修改");
        assertThrows(UnsupportedOperationException.class,
                () -> result.getRawList().add("10.0.0.1"), "XFF 链应不可修改");
        assertFalse(result.toString().contains("192.0.2.1"), "toString 不应包含 XFF 内容");
    }

    @Test
    void shouldCreateOneSnapshotAndPublishOnceUnderConcurrentReads() throws Exception {
        AtomicInteger publishCount = new AtomicInteger();
        DefaultXffCaptureService service = new DefaultXffCaptureService(event -> publishCount.incrementAndGet());
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "192.0.2.1");
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<XffChain>> futureList = new java.util.ArrayList<>();
        try {
            for (int index = 0; index < threadCount; index++) {
                futureList.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.capture(request);
                }));
            }
            ready.await();
            start.countDown();
            XffChain expected = futureList.get(0).get();
            for (Future<XffChain> future : futureList) {
                assertSame(expected, future.get(), "并发读取应返回同一快照实例");
            }
        } finally {
            executor.shutdownNow();
        }

        log.info("并发读取线程数：{}，事件发布次数：{}", threadCount, publishCount.get());
        assertEquals(1, publishCount.get(), "同一请求并发读取最多发布一次事件");
    }

    @Test
    void shouldCacheSnapshotBeforeEventPublicationFailure() {
        int[] publishCount = {0};
        ApplicationEventPublisher failingPublisher = event -> {
            publishCount[0]++;
            throw new RuntimeException("test listener failure");
        };
        DefaultXffCaptureService service = new DefaultXffCaptureService(failingPublisher);
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "192.0.2.1");

        XffChain first = service.capture(request);
        XffChain second = service.capture(request);

        log.info("事件发布失败后的调用次数：{}", publishCount[0]);
        assertSame(first, second, "事件失败后仍应返回首次快照");
        assertEquals(1, publishCount[0], "事件失败后不应重试发布");
    }

    @Test
    void shouldRejectNullRequest() {
        XffCaptureValidationException exception = assertThrows(XffCaptureValidationException.class,
                () -> xffCaptureService.capture(null), "null 请求应抛模块校验异常");

        log.info("null 请求错误码：{}", exception.getErrorCode());
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, exception.getErrorCode(), "错误码应表示必填值缺失");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/resource");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
