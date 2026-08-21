package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import io.github.surezzzzzz.sdk.http.xff.test.support.TestXffCaptureEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XFF Capture Filter 顺序集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.http.xff.capture.order=101",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import({XffCaptureFilterOrderIntegrationTest.TestController.class,
        XffCaptureFilterOrderIntegrationTest.XffBusinessViewFilterConfiguration.class})
class XffCaptureFilterOrderIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldCaptureWrappedXffWhenCaptureFilterRunsAfterWrapper() {
        ResponseEntity<String> response = restTemplate.getForEntity("/xff-order-business-view", String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("Capture Filter 顺序在包装 Filter 之后：响应状态={}，业务响应={}，xffPresent={}，rawList={}",
                response.getStatusCodeValue(), response.getBody(), event.getXffChain().isPresent(),
                event.getXffChain().getRawList());
        assertEquals(200, response.getStatusCodeValue(), "业务请求应正常响应");
        assertTrue(response.getBody().contains("198.51.100.10, 10.0.0.10"),
                "业务阶段应读取包装后的 XFF");
        assertEquals(1, eventListener.snapshot().size(), "一次请求只能发布一个事件");
        assertTrue(event.getXffChain().isPresent(),
                "Capture Filter 排在已知包装 Filter 后时应采集非空 XFF");
    }

    @TestConfiguration
    static class XffBusinessViewFilterConfiguration {

        @Bean
        FilterRegistrationBean<XffBusinessViewFilter> xffBusinessViewFilter() {
            FilterRegistrationBean<XffBusinessViewFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new XffBusinessViewFilter());
            registration.addUrlPatterns("/*");
            registration.setOrder(100);
            return registration;
        }
    }

    static class XffBusinessViewFilter extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !"/xff-order-business-view".equals(request.getRequestURI());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            filterChain.doFilter(new XffHeaderRequestWrapper(request,
                    "198.51.100.10, 10.0.0.10"), response);
        }
    }

    static class XffHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String xffHeaderValue;

        XffHeaderRequestWrapper(HttpServletRequest request, String xffHeaderValue) {
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
            return Collections.enumeration(Collections.singletonList(
                    SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR));
        }
    }

    @RestController
    static class TestController {

        @Autowired
        private XffCaptureService xffCaptureService;

        @GetMapping("/xff-order-business-view")
        String businessView(HttpServletRequest request) {
            return request.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR)
                    + "|present=" + xffCaptureService.capture(request).isPresent();
        }
    }
}
