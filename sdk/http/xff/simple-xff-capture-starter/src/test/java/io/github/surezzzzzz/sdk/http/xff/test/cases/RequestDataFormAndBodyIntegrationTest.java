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
 * 表单参数与请求体同时采集的真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=request-data-form-body")
@Import(RequestDataFormAndBodyIntegrationTest.FormAndBodyTestConfiguration.class)
class RequestDataFormAndBodyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldPreserveCompleteFormParametersAndBodyWhenBothCaptureDimensionsEnabled() {
        String requestBody = "tag=one&tag=two&name=abcdefghijklmnopqrstuvwxyz";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                "/request-form-body-and-form", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("Form 与 Body 同时采集真实 HTTP 结果：status={}，eventCount={}，formStatus={}，"
                        + "bodyStatus={}，capturedBytes={}，controllerBodyLength={}",
                response.getStatusCodeValue(), eventListener.snapshot().size(),
                event.getRequestData().getFormParameters().getStatus(),
                event.getRequestData().getBody().getStatus(),
                event.getRequestData().getBody().getCapturedByteCount(),
                response.getBody() == null ? 0 : response.getBody().length());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("tag=one,two|" + requestBody, response.getBody(),
                "Form 与 Body 同时采集不得破坏 Controller 参数或完整请求体");
        assertEquals(1, eventListener.snapshot().size());
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                event.getRequestData().getFormParameters().getStatus());
        assertEquals(2, event.getRequestData().getFormParameters().getValues().get("tag").size());
        assertEquals("abcdefghijklmnopqrstuvwxyz",
                event.getRequestData().getFormParameters().getValues().get("name").get(0));
        assertEquals(RequestBodyCaptureStatus.TRUNCATED,
                event.getRequestData().getBody().getStatus());
        assertEquals(8L, event.getRequestData().getBody().getCapturedByteCount());
        assertEquals(requestBody.substring(0, 8), event.getRequestData().getBody().getText());
    }

    @TestConfiguration
    static class FormAndBodyTestConfiguration {

        @Bean
        FormAndBodyTestController formAndBodyTestController() {
            return new FormAndBodyTestController();
        }
    }

    @RestController
    static class FormAndBodyTestController {

        @PostMapping("/request-form-body-and-form")
        String formAndBody(@RequestParam("tag") String[] tags,
                           @RequestBody String requestBody) {
            return "tag=" + String.join(",", tags) + "|" + requestBody;
        }
    }
}
