package io.github.surezzzzzz.sdk.feedback.client.test.cases;

import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import io.github.surezzzzzz.sdk.feedback.client.resttemplate.FeedbackRestTemplateClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

@Slf4j
class FeedbackRestTemplateClientValidationTest {

    @Test
    void rejectsMissingRestTemplate() {
        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackRestTemplateClient(null, "http://localhost"));

        assertEquals("restTemplate", exception.getParameter());
        log.info("校验缺少 akskClientRestTemplate 时拒绝装配");
    }

    @Test
    void rejectsBlankBaseUrl() {
        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackRestTemplateClient(new RestTemplate(), " "));

        assertEquals("baseUrl", exception.getParameter());
        log.info("校验 baseUrl 为空时拒绝装配");
    }

    @Test
    void rejectsBlankResourcePathBeforeSendingRequest() {
        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(new RestTemplate(), "http://localhost");

        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> client.getSubmission(" "));

        assertEquals("path", exception.getParameter());
        log.info("校验资源路径为空时不发送请求");
    }

    @Test
    void requiresIdempotencyKeyForSubmission() {
        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(new RestTemplate(), "http://localhost");

        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> client.createSubmission(Collections.<String, Object>emptyMap(), null, " ", null));

        assertEquals("idempotencyKey", exception.getParameter());
        log.info("校验提交缺少幂等键时拒绝发送");
    }

    @Test
    void rejectsNullRequestBodiesBeforeSendingRequest() {
        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(new RestTemplate(), "http://localhost");

        assertEquals("submission", assertThrows(FeedbackClientValidationException.class,
                () -> client.createSubmission(null, null, "idem-1", null)).getParameter());
        assertEquals("handling", assertThrows(FeedbackClientValidationException.class,
                () -> client.handleSubmission("submission-1", null, null)).getParameter());
        assertEquals("application", assertThrows(FeedbackClientValidationException.class,
                () -> client.createApplication(null, null)).getParameter());
        assertEquals("application", assertThrows(FeedbackClientValidationException.class,
                () -> client.updateApplication("sample-app", null, null)).getParameter());
        log.info("校验提交、处理和应用登记请求体为空时均拒绝发送");
    }

    @Test
    void rejectsNullAttachmentEntryBeforeSendingRequest() {
        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(new RestTemplate(), "http://localhost");

        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> client.createSubmission(new HashMap<String, Object>(),
                        Collections.singletonList(null), "idem-1", null));

        assertEquals("attachments", exception.getParameter());
        log.info("校验附件列表含空条目时拒绝发送");
    }

    @Test
    void rejectsInvalidAttachmentContentTypeBeforeSendingRequest() {
        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(new RestTemplate(), "http://localhost");

        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> client.createSubmission(new HashMap<String, Object>(),
                        Collections.singletonList(new FeedbackAttachmentPart("a.txt", "not a media type",
                                new byte[] {1})), "idem-1", null));

        assertEquals("contentType", exception.getParameter());
        log.info("校验附件媒体类型非法时拒绝发送");
    }

    @Test
    void encodesPathValueAsSingleUriSegment() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("http://localhost/api/feedback/submissions/id%2Fpart"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"id\":\"id/part\",\"applicationCode\":\"sample-app\","
                        + "\"kind\":\"proactive\",\"type\":\"bug\",\"score\":8,"
                        + "\"content\":\"content\",\"status\":\"submitted\",\"rowVersion\":0,"
                        + "\"attachments\":[],\"createdTime\":\"2026-10-24T12:00:00+08:00\"}",
                        MediaType.APPLICATION_JSON));

        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(restTemplate, "http://localhost");
        assertEquals("id/part", client.getSubmission("id/part").getId());
        server.verify();
        log.info("校验资源标识按单个 URI 路径段编码");
    }

    @Test
    void sendsListQueryAsUriParameters() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("http://localhost/api/feedback/submissions?page=2"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"items\":[],\"total\":0,\"page\":2,\"size\":20}",
                        MediaType.APPLICATION_JSON));

        FeedbackRestTemplateClient client = new FeedbackRestTemplateClient(restTemplate, "http://localhost");
        assertEquals(2, client.listSubmissions(Collections.<String, Object>singletonMap("page", 2)).getPage());
        server.verify();
        log.info("校验查询参数按 URI 编码发送");
    }
}
