package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import io.github.surezzzzzz.sdk.http.xff.test.support.TestXffCaptureEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 请求数据自动采集真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=request-data-json")
@Import(RequestDataCaptureIntegrationTest.RequestDataTestConfiguration.class)
class RequestDataCaptureIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldCaptureBoundedRequestDataAndPreserveControllerBody() {
        String requestBody = "{\"message\":\"long-body\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-data/body?tag=one&tag=two", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("请求数据真实 HTTP 结果：status={}，eventCount={}，queryStatus={}，bodyStatus={}，"
                        + "capturedBytes={}，controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getQueryParameters().getStatus(),
                event.getRequestData().getBody().getStatus(),
                event.getRequestData().getBody().getCapturedByteCount(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(requestBody, response.getBody(), "回放请求必须让 Controller 收到完整请求体");
        assertEquals(1, eventListener.snapshot().size(), "真实请求只能发布一个事件");
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getQueryParameters().getStatus());
        assertEquals(2, event.getRequestData().getQueryParameters().getValues().get("tag").size());
        assertEquals(RequestBodyCaptureStatus.TRUNCATED,
                event.getRequestData().getBody().getStatus());
        assertEquals(8L, event.getRequestData().getBody().getCapturedByteCount());
        assertEquals(requestBody.substring(0, 8), event.getRequestData().getBody().getText());
    }

    @Test
    void shouldPreserveJsonBodyAndQueryParametersForController() {
        String requestBody = "{\"message\":\"long-body\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-data/body-with-query?source=web&source=api", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("JSON Body 与 Query 真实 HTTP 结果：status={}，eventCount={}，bodyStatus={}，"
                        + "controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getBody().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("source=web,api|" + requestBody, response.getBody(),
                "JSON 请求回放不得破坏 Controller 的 Query 参数或完整请求体");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getQueryParameters().getStatus());
        assertEquals(RequestBodyCaptureStatus.TRUNCATED,
                event.getRequestData().getBody().getStatus());
    }

    @Test
    void shouldCaptureCrudMethodsWithMethodSpecificRules() {
        assertCrudRequest(HttpMethod.GET, "tag=one&tag=two", "GET");
        assertCrudRequest(HttpMethod.POST, "{\"method\":\"POST\"}", "POST");
        assertCrudRequest(HttpMethod.PUT, "{\"method\":\"PUT\"}", "PUT");
        assertCrudRequest(HttpMethod.PATCH, "{\"method\":\"PATCH\"}", "PATCH");
        assertCrudRequest(HttpMethod.DELETE, "{\"method\":\"DELETE\"}", "DELETE");
    }

    @Test
    void shouldSkipDisallowedContentTypeWithoutBreakingControllerBody() {
        String requestBody = "plain-text-body";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-data/body", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("不允许 Content-Type 的真实 HTTP 结果：status={}，eventCount={}，bodyStatus={}，"
                        + "controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getBody().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(requestBody, response.getBody(), "跳过采集时也不得破坏业务请求体");
        assertEquals(RequestBodyCaptureStatus.CONTENT_TYPE_SKIPPED,
                event.getRequestData().getBody().getStatus());
        assertEquals(1, eventListener.snapshot().size());
    }

    @Test
    void shouldRejectExactBlacklistBeforeReadingRequestData() {
        String requestBody = "{\"message\":\"excluded\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-data/excluded?tag=one", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("请求数据黑名单真实 HTTP 结果：status={}，eventCount={}，queryStatus={}，bodyStatus={}，"
                        + "controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getQueryParameters().getStatus(),
                event.getRequestData().getBody().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(requestBody, response.getBody(), "黑名单请求也必须保留原始请求体");
        assertEquals(1, eventListener.snapshot().size(), "真实请求只能发布一个事件");
        assertEquals(RequestDataCaptureStatus.RULE_NOT_MATCHED,
                event.getRequestData().getQueryParameters().getStatus());
        assertEquals(RequestBodyCaptureStatus.RULE_NOT_MATCHED,
                event.getRequestData().getBody().getStatus());
    }

    private void assertCrudRequest(HttpMethod method, String requestBody, String expectedMethod) {
        eventListener.clear();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String uri = HttpMethod.GET.equals(method)
                ? "/request-data/crud?tag=one&tag=two" : "/request-data/crud";
        ResponseEntity<String> response = restTemplate.exchange(
                uri, method, new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("CRUD 方法真实 HTTP 结果：method={}，status={}，eventMethod={}，queryStatus={}，bodyStatus={}，"
                        + "controllerBodyLength={}",
                method, response.getStatusCodeValue(), event.getRequestMethod(),
                event.getRequestData().getQueryParameters().getStatus(),
                event.getRequestData().getBody().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, eventListener.snapshot().size(), "每个 CRUD 请求应只发布一个事件");
        assertEquals(expectedMethod, event.getRequestMethod());
        if (HttpMethod.GET.equals(method)) {
            assertEquals(RequestDataCaptureStatus.CAPTURED,
                    event.getRequestData().getQueryParameters().getStatus());
            assertEquals(2, event.getRequestData().getQueryParameters().getValues().get("tag").size());
            assertEquals(RequestBodyCaptureStatus.NO_BODY,
                    event.getRequestData().getBody().getStatus());
            assertEquals("GET", response.getBody());
        } else {
            assertEquals(RequestDataCaptureStatus.ABSENT,
                    event.getRequestData().getQueryParameters().getStatus());
            assertEquals(RequestBodyCaptureStatus.TRUNCATED,
                    event.getRequestData().getBody().getStatus());
            assertEquals(requestBody.substring(0, 8), event.getRequestData().getBody().getText());
            assertEquals(requestBody, response.getBody(), "CRUD Controller 应收到完整请求体");
        }
    }

    @TestConfiguration
    static class RequestDataTestConfiguration {

        @Bean
        RestTemplateBuilder requestDataTestRestTemplateBuilder() {
            return new RestTemplateBuilder().requestFactory(
                    org.springframework.http.client.HttpComponentsClientHttpRequestFactory::new);
        }

        @Bean
        RequestDataTestController requestDataTestController() {
            return new RequestDataTestController();
        }
    }

    @RestController
    static class RequestDataTestController {

        @PostMapping("/request-data/body")
        String body(@RequestBody String requestBody) {
            return requestBody;
        }

        @PostMapping("/request-data/body-with-query")
        String bodyWithQuery(@RequestParam("source") String[] source,
                             @RequestBody String requestBody) {
            return "source=" + String.join(",", source) + "|" + requestBody;
        }

        @org.springframework.web.bind.annotation.GetMapping("/request-data/crud")
        String get() {
            return "GET";
        }

        @PostMapping("/request-data/crud")
        String post(@RequestBody String requestBody) {
            return requestBody;
        }

        @org.springframework.web.bind.annotation.PutMapping("/request-data/crud")
        String put(@RequestBody String requestBody) {
            return requestBody;
        }

        @org.springframework.web.bind.annotation.PatchMapping("/request-data/crud")
        String patch(@RequestBody String requestBody) {
            return requestBody;
        }

        @org.springframework.web.bind.annotation.DeleteMapping("/request-data/crud")
        String delete(@RequestBody String requestBody) {
            return requestBody;
        }

        @PostMapping("/request-data/excluded")
        String excluded(@RequestBody String requestBody) {
            return requestBody;
        }
    }
}
