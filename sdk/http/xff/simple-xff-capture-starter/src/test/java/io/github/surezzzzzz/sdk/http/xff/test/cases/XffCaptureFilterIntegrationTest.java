package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import io.github.surezzzzzz.sdk.http.xff.test.support.TestXffCaptureEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * XFF 自动采集 Filter 真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(XffCaptureFilterIntegrationTest.TestController.class)
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

        log.info("带 query 请求的事件 URI：{}", events.get(0).getRequestUri());
        assertEquals(200, response.getStatusCodeValue(), "请求应正常响应");
        assertEquals(1, events.size(), "请求应发布一个事件");
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
    void shouldCaptureNotFoundRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity("/missing-resource", String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        log.info("404 响应状态：{}，采集事件数量：{}", response.getStatusCodeValue(), events.size());
        assertEquals(404, response.getStatusCodeValue(), "缺失资源应保持 404");
        assertEquals(1, events.size(), "未进入 Controller 的请求仍应采集");
        assertFalse(events.get(0).getXffChain().isPresent(), "未携带 XFF 时应记录空事实");
    }

    @RestController
    static class TestController {

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
