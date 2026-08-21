package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF 自动采集 Filter 真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration")
@Import({XffCaptureFilterIntegrationTest.TestController.class,
        XffCaptureFilterIntegrationTest.XffBusinessViewFilterConfiguration.class})
class XffCaptureFilterIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldCaptureBusinessRequestViewWhenDownstreamFilterWrapsRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity("/xff-business-view", String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("真实 MVC 请求视图复现：响应状态={}，事件数量={}，业务阶段 XFF={}",
                response.getStatusCodeValue(), events.size(), response.getBody());
        assertEquals(200, response.getStatusCodeValue(), "业务 Controller 应正常响应");
        assertTrue(response.getBody().contains("198.51.100.10, 10.0.0.10"),
                "业务阶段应能读取后置 wrapper 暴露的 XFF");
        assertEquals(1, events.size(), "真实请求只应发布一次事件");
        assertTrue(events.get(0).getXffChain().isPresent(),
                "最低优先级 Capture 应采集后置 wrapper 暴露的 XFF");
        assertEquals(2, events.get(0).getXffChain().getRawList().size(),
                "最低优先级 Capture 应保留完整 XFF 链");
    }

    @Test
    void shouldCaptureWithoutControllerIntegrationCode() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "192.0.2.10, unknown, 10.0.0.1");

        ResponseEntity<String> response = restTemplate.exchange("/xff-test", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("HTTP 响应状态：{}，采集事件数量：{}", response.getStatusCodeValue(), events.size());
        assertEquals(200, response.getStatusCodeValue(), "Controller 应正常响应");
        assertEquals(1, events.size(), "一次 HTTP 请求应发布一次事件");
        assertEquals(3, events.get(0).getXffChain().getRawList().size(), "应采集完整 XFF 链");
    }

    @Test
    void shouldExcludeQueryStringFromEvent() {
        ResponseEntity<String> response = restTemplate.getForEntity("/xff-test?secret=value", String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("带 query 请求响应状态：{}，采集事件数量：{}", response.getStatusCodeValue(), events.size());
        assertEquals(200, response.getStatusCodeValue(), "请求应正常响应");
        assertEquals(1, events.size(), "请求应发布一个事件");
        log.info("带 query 请求的事件 URI：{}", events.get(0).getRequestUri());
        assertEquals("/xff-test", events.get(0).getRequestUri(), "事件 URI 不应包含 query string");
    }

    @Test
    void shouldCaptureBadRequestBeforeControllerInvocation() {
        ResponseEntity<String> response = restTemplate.getForEntity("/xff-number?value=invalid", String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("参数绑定失败响应状态：{}，事件数量：{}", response.getStatusCodeValue(), events.size());
        assertEquals(400, response.getStatusCodeValue(), "参数绑定失败应保持 400");
        assertEquals(1, events.size(), "参数绑定失败仍应在入口采集");
    }

    @Test
    void shouldNotCaptureConfiguredExcludedPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("排除路径响应状态：{}，采集事件数量：{}", response.getStatusCodeValue(), events.size());
        assertEquals(404, response.getStatusCodeValue(), "排除路径应保持既有 404 响应");
        assertEquals(0, events.size(), "命中排除清单的请求不应发布自动采集事件");
    }

    @Test
    void shouldCaptureNotFoundRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity("/missing-resource", String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("404 响应状态：{}，采集事件数量：{}", response.getStatusCodeValue(), events.size());
        assertEquals(404, response.getStatusCodeValue(), "缺失资源应保持 404");
        assertEquals(1, events.size(), "未进入 Controller 的请求仍应采集");
        assertFalse(events.get(0).getXffChain().isPresent(), "未携带 XFF 时应记录空事实");
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
            return !"/xff-business-view".equals(request.getRequestURI());
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

    @RestController
    static class TestController {

        @Autowired
        private XffCaptureService xffCaptureService;

        @GetMapping("/xff-business-view")
        String businessView(HttpServletRequest request) {
            XffChain chain = xffCaptureService.capture(request);
            return request.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR)
                    + "|present=" + chain.isPresent();
        }

        @GetMapping("/xff-test")
        String test() {
            return "ok";
        }

        @GetMapping("/xff-number")
        String number(int value) {
            return String.valueOf(value);
        }
    }
}
