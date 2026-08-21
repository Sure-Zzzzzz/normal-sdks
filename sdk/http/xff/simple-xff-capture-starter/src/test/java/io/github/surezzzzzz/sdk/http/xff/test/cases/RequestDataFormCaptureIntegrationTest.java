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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 表单参数自动采集真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=request-data-form")
@Import(RequestDataFormCaptureIntegrationTest.FormTestConfiguration.class)
class RequestDataFormCaptureIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldCaptureRepeatedFormParametersAndPreserveControllerBody() {
        String requestBody = "tag=one&tag=two&name=alice";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("表单真实 HTTP 结果：status={}，eventCount={}，formStatus={}，bodyStatus={}，"
                        + "tagCount={}，controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getFormParameters().getStatus(),
                event.getRequestData().getBody().getStatus(),
                event.getRequestData().getFormParameters().getValues().get("tag").size(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(requestBody, response.getBody(), "表单预读后 Controller 必须收到完整原文");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getFormParameters().getStatus());
        assertEquals(2, event.getRequestData().getFormParameters().getValues().get("tag").size());
        assertEquals("alice", event.getRequestData().getFormParameters().getValues().get("name").get(0));
        assertEquals(RequestBodyCaptureStatus.DISABLED,
                event.getRequestData().getBody().getStatus());
    }

    @Test
    void shouldPreserveControllerBodyAndQueryParametersWithoutCaptureWrapper() {
        String requestBody = "tag=one&tag=two&name=alice";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-with-query-raw?source=web&source=api", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);

        log.info("未包装表单与 Query 基线：status={}，controllerResponseLength={}",
                response.getStatusCodeValue(), response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("source=web,api|source=web&source=api&" + requestBody, response.getBody(),
                "未包装请求的 Servlet 参数解析语义应作为回放基线");
        assertEquals(0, eventListener.snapshot().size(), "顶层排除路径不得自动发布事件");
    }

    @Test
    void shouldPreserveControllerBodyAndQueryParametersWhenOnlyFormCaptureIsEnabled() {
        String requestBody = "tag=one&tag=two&name=alice";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-with-query?source=web&source=api", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("表单与 Query 真实 HTTP 结果：status={}，eventCount={}，queryStatus={}，formStatus={}，"
                        + "controllerResponseLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getQueryParameters().getStatus(),
                event.getRequestData().getFormParameters().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("source=web,api|source=web&source=api&" + requestBody, response.getBody(),
                "回放请求必须保持未包装请求的 Servlet 参数解析语义");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.DISABLED,
                event.getRequestData().getQueryParameters().getStatus());
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getFormParameters().getStatus());
    }

    @Test
    void shouldNotBreakControllerWhenFormBodyExceedsCaptureLimit() {
        String requestBody = "tag=abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
                + "&name=abcdefghijklmnopqrstuvwxyz";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-overflow", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("超限表单真实 HTTP 结果：status={}，eventCount={}，formStatus={}，"
                        + "controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getFormParameters().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("tag=abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz|" + requestBody,
                response.getBody(), "表单超限时不得破坏 Controller 参数或完整请求体");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getFormParameters().getStatus());
    }

    @Test
    void shouldKeepSameNameContainerSemanticsWithoutCaptureWrapper() {
        String requestBody = "source=form-one&source=form-two";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-same-name-raw?source=query-one&source=query-two", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);

        log.info("同名未包装表单基线：status={}，controllerBodyLength={}",
                response.getStatusCodeValue(), response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("source=query-one,query-two,form-one,form-two|source=query-one&source=query-two&"
                + requestBody, response.getBody());
        assertEquals(0, eventListener.snapshot().size(), "顶层排除路径不得自动发布事件");
    }

    @Test
    void shouldKeepFormValuesWhenQueryAndFormUseSameParameterName() {
        String requestBody = "source=form-one&source=form-two";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-same-name?source=query-one&source=query-two", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("Query/Form 同名真实 HTTP 结果：status={}，eventCount={}，formStatus={}，"
                        + "controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getFormParameters().getStatus(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("source=query-one,query-two,form-one,form-two|source=query-one&source=query-two&"
                        + requestBody, response.getBody(),
                "回放请求必须保持同名参数的容器解析语义");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getFormParameters().getStatus());
        assertEquals(2, event.getRequestData().getFormParameters().getValues().get("source").size());
        assertEquals("form-one", event.getRequestData().getFormParameters().getValues().get("source").get(0));
    }

    @Test
    void shouldMarkEmptyFormWithoutBreakingQueryOrBody() {
        String requestBody = "";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> rawResponse = restTemplate.exchange(
                "/request-form-empty-raw?source=web", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        assertEquals(HttpStatus.OK, rawResponse.getStatusCode());
        assertEquals(0, eventListener.snapshot().size(), "顶层排除路径不得自动发布事件");

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-empty?source=web", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("空表单真实 HTTP 结果：rawStatus={}，wrappedStatus={}，rawBodyLength={}，wrappedBodyLength={}，"
                        + "eventCount={}，formStatus={}",
                rawResponse.getStatusCodeValue(), response.getStatusCodeValue(),
                rawResponse.getBody() == null ? 0 : rawResponse.getBody().length(),
                response.getBody() == null ? 0 : response.getBody().length(),
                eventListener.snapshot().size(), event.getRequestData().getFormParameters().getStatus());
        assertEquals(rawResponse.getBody(), response.getBody(),
                "请求数据采集包装不得改变空表单请求的 Controller 结果");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.ABSENT,
                event.getRequestData().getFormParameters().getStatus());
    }

    @TestConfiguration
    static class FormTestConfiguration {

        @Bean
        FormTestController formTestController() {
            return new FormTestController();
        }
    }

    @RestController
    static class FormTestController {

        @PostMapping("/request-form")
        String form(@RequestBody String requestBody) {
            return requestBody;
        }

        @PostMapping("/request-form-with-query")
        String formWithQuery(@RequestParam("source") String[] source,
                             @RequestBody String requestBody) {
            return "source=" + String.join(",", source) + "|" + requestBody;
        }

        @PostMapping("/request-form-with-query-raw")
        String formWithQueryRaw(@RequestParam("source") String[] source,
                                @RequestBody String requestBody) {
            return "source=" + String.join(",", source) + "|" + requestBody;
        }

        @PostMapping("/request-form-overflow")
        String formOverflow(@RequestParam("tag") String tag, @RequestBody String requestBody) {
            return "tag=" + tag + "|" + requestBody;
        }

        @PostMapping("/request-form-same-name")
        String formSameName(@RequestParam("source") String[] source,
                            @RequestBody String requestBody) {
            return "source=" + String.join(",", source) + "|" + requestBody;
        }

        @PostMapping("/request-form-same-name-raw")
        String formSameNameRaw(@RequestParam("source") String[] source,
                               @RequestBody String requestBody) {
            return "source=" + String.join(",", source) + "|" + requestBody;
        }

        @PostMapping("/request-form-empty")
        String formEmpty(@RequestParam("source") String source,
                         @RequestBody String requestBody) {
            return "source=" + source + "|" + requestBody;
        }

        @PostMapping("/request-form-empty-raw")
        String formEmptyRaw(@RequestParam("source") String source,
                            @RequestBody String requestBody) {
            return "source=" + source + "|" + requestBody;
        }
    }
}
