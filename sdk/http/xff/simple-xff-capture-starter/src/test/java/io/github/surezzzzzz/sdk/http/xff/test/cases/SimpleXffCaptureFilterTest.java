package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
import io.github.surezzzzzz.sdk.http.xff.filter.SimpleXffCaptureFilter;
import io.github.surezzzzzz.sdk.http.xff.service.DefaultXffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.support.ReplayableRequestBodyWrapper;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataCapturePreparer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * XFF 自动采集 Filter 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleXffCaptureFilterTest {

    @Test
    void shouldContinueChainWhenCaptureFails() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        doThrow(new RuntimeException("test capture failure")).when(service).capture(any());
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service);
        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (request, response) -> invoked.set(true);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        log.info("采集失败后的下游 Filter 调用状态：{}", invoked.get());
        assertTrue(invoked.get(), "采集失败不能阻断原 Filter 链");
        log.info("默认路径应调用一次 XFF 采集服务");
        verify(service, times(1)).capture(any());
    }

    @Test
    void shouldStillCaptureXffWhenRequestDataPreparationFails() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureProperties properties = new SimpleXffCaptureProperties();
        properties.getRequestData().getQueryParameters().setEnabled(true);
        SimpleXffCaptureProperties.RequestDataRule rule =
                new SimpleXffCaptureProperties.RequestDataRule();
        rule.setMethod("GET");
        rule.setPathPattern("/request-data-failure");
        properties.getRequestData().setWhitelist(Collections.singletonList(rule));
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service,
                Collections.<String>emptyList(), new RequestDataCapturePreparer(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/request-data-failure") {
            @Override
            public String getQueryString() {
                throw new RuntimeException("test request-data preparation failure");
            }
        };
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(),
                (filterRequest, response) -> invoked.set(true));

        log.info("请求数据准备失败后的 XFF 采集与下游状态：captured={}，downstream={}",
                mockingDetails(service).getInvocations().size(), invoked.get());
        assertTrue(invoked.get(), "请求数据准备失败不能阻断原 Filter 链");
        verify(service, times(1)).capture(request);
    }

    @Test
    void shouldReproduceEmptyFilterViewAndNonEmptyBusinessView() throws Exception {
        List<XffCaptureEvent> events = new ArrayList<XffCaptureEvent>();
        XffCaptureService service = new DefaultXffCaptureService(
                event -> events.add((XffCaptureEvent) event));
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service);
        MockHttpServletRequest entryRequest = new MockHttpServletRequest("GET", "/xff-test");
        AtomicBoolean businessInvoked = new AtomicBoolean(false);
        HttpServletRequest[] businessRequest = new HttpServletRequest[1];

        filter.doFilter(entryRequest, new MockHttpServletResponse(), (request, response) -> {
            HttpServletRequest wrappedRequest = new XffHeaderRequestWrapper(
                    (HttpServletRequest) request, "198.51.100.10, 10.0.0.10");
            businessRequest[0] = wrappedRequest;
            log.info("业务阶段读取 XFF：identity={}，type={}，header={}",
                    System.identityHashCode(wrappedRequest), wrappedRequest.getClass().getName(),
                    wrappedRequest.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR));
            XffChain businessChain = service.capture(wrappedRequest);
            businessInvoked.set(true);
            assertTrue(wrappedRequest.getHeaderNames().hasMoreElements(),
                    "业务阶段应能读取请求 Header 名称");
            assertEquals("198.51.100.10, 10.0.0.10",
                    wrappedRequest.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR),
                    "业务阶段应能读取 XFF Header");
            assertFalse(businessChain.isPresent(),
                    "当前实现复用入口快照，业务阶段不会覆盖已缓存的空链");
        });

        log.info("Filter 入口请求：identity={}，type={}，事件数量={}，业务是否执行={}",
                System.identityHashCode(entryRequest), entryRequest.getClass().getName(),
                events.size(), businessInvoked.get());
        assertTrue(businessInvoked.get(), "Filter 采集后必须继续执行下游业务链");
        assertNotNull(businessRequest[0], "下游应收到业务阶段 RequestWrapper");
        assertNotSame(entryRequest, businessRequest[0], "业务阶段应使用独立 RequestWrapper");
        assertFalse(events.get(0).getXffChain().isPresent(),
                "当前事件仍固定为入口阶段的空 XFF 视图");
        assertEquals(1, events.size(), "入口与业务阶段只能发布一次事件");
    }

    @Test
    void shouldSkipAutomaticCaptureWhenRequestMatchesExactExcludedPath() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service,
                Arrays.asList("/actuator/prometheus"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setQueryString("ignored=value");
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(), (filterRequest, response) -> invoked.set(true));

        log.info("精确排除路径后的下游 Filter 调用状态：{}", invoked.get());
        assertTrue(invoked.get(), "排除路径不能阻断原 Filter 链");
        log.info("精确排除路径不应调用 XFF 采集服务");
        verify(service, never()).capture(any());
    }

    @Test
    void shouldSkipAutomaticCaptureWhenWildcardMatchesApplicationPath() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service, Arrays.asList("/actuator/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gateway/actuator/health");
        request.setContextPath("/gateway");

        filter.doFilter(request, new MockHttpServletResponse(), (filterRequest, response) -> {
        });

        log.info("上下文路径 [{}] 下的排除路径 [{}] 未触发自动采集", request.getContextPath(), request.getRequestURI());
        verify(service, never()).capture(any());
    }

    @Test
    void shouldSkipAutomaticCaptureWhenContextRootMatchesExcludedPath() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service, Arrays.asList("/"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gateway");
        request.setContextPath("/gateway");

        filter.doFilter(request, new MockHttpServletResponse(), (filterRequest, response) -> {
        });

        log.info("上下文根路径 [{}] 应匹配排除模式 /", request.getRequestURI());
        verify(service, never()).capture(any());
    }

    @Test
    void shouldCaptureWhenWildcardDoesNotMatchPathBoundary() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service, Arrays.asList("/actuator/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuatorish/prometheus");

        filter.doFilter(request, new MockHttpServletResponse(), (filterRequest, response) -> {
        });

        log.info("边界路径 [{}] 不应命中 /actuator/**，应调用一次 XFF 采集服务", request.getRequestURI());
        verify(service, times(1)).capture(request);
    }

    @Test
    void shouldDeleteFormBodyReplayFileAfterFilterChainCompletes() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureProperties properties = new SimpleXffCaptureProperties();
        properties.getRequestData().getFormParameters().setEnabled(true);
        properties.getRequestData().getBody().setEnabled(true);
        properties.getRequestData().getBody().setMaxBytes(8L);
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList("application/x-www-form-urlencoded"));
        SimpleXffCaptureProperties.RequestDataRule rule =
                new SimpleXffCaptureProperties.RequestDataRule();
        rule.setMethod("POST");
        rule.setPathPattern("/request-form-body");
        properties.getRequestData().setWhitelist(Collections.singletonList(rule));
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service,
                Collections.<String>emptyList(), preparer);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/request-form-body");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("tag=one&name=abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));
        AtomicReference<ReplayableRequestBodyWrapper> wrappedRequest =
                new AtomicReference<ReplayableRequestBodyWrapper>();

        filter.doFilter(request, new MockHttpServletResponse(), (filterRequest, response) -> {
            ReplayableRequestBodyWrapper replayableRequest =
                    (ReplayableRequestBodyWrapper) filterRequest;
            wrappedRequest.set(replayableRequest);
            replayableRequest.getInputStream().close();
        });

        log.info("Filter 链结束后请求体回放文件清理状态已验证");
        assertNotNull(wrappedRequest.get(), "命中 Form+Body 组合路径时应使用回放包装请求");
        assertThrows(XffCaptureValidationException.class,
                () -> wrappedRequest.get().getInputStream(),
                "Filter 链结束后不应继续读取已清理的临时回放文件");
    }

    @Test
    void shouldPropagateDownstreamServletException() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service);
        FilterChain chain = (request, response) -> {
            throw new ServletException("test downstream failure");
        };

        log.info("验证下游 ServletException 不被采集 Filter 吞掉");
        ServletException exception = assertThrows(ServletException.class,
                () -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain),
                "下游 ServletException 应按原样传播");
        assertEquals("test downstream failure", exception.getMessage(), "应保留下游异常消息");
    }

    @Test
    void shouldPropagateDownstreamIoException() throws Exception {
        XffCaptureService service = mock(XffCaptureService.class);
        SimpleXffCaptureFilter filter = new SimpleXffCaptureFilter(service);
        FilterChain chain = (request, response) -> {
            throw new IOException("test io failure");
        };

        log.info("验证下游 IOException 不被采集 Filter 吞掉");
        IOException exception = assertThrows(IOException.class,
                () -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain),
                "下游 IOException 应按原样传播");
        assertEquals("test io failure", exception.getMessage(), "应保留下游 IO 异常消息");
    }

    private static final class XffHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String xffHeaderValue;

        private XffHeaderRequestWrapper(HttpServletRequest request, String xffHeaderValue) {
            super(request);
            this.xffHeaderValue = xffHeaderValue;
        }

        @Override
        public String getHeader(String name) {
            if (SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR.equalsIgnoreCase(name)) {
                return xffHeaderValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(xffHeaderValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<String>();
            Enumeration<String> sourceNames = super.getHeaderNames();
            if (sourceNames != null) {
                while (sourceNames.hasMoreElements()) {
                    names.add(sourceNames.nextElement());
                }
            }
            names.add(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR);
            return Collections.enumeration(names);
        }
    }
}
