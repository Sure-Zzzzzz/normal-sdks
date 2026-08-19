package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.filter.SimpleXffCaptureFilter;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * XFF 自动采集 Filter 故障边界测试。
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
        verify(service, times(1)).capture(any());
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
}
