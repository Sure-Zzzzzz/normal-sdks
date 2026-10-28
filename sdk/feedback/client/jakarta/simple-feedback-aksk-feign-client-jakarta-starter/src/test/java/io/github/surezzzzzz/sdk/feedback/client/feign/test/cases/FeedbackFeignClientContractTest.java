package io.github.surezzzzzz.sdk.feedback.client.feign.test.cases;

import feign.FeignException;
import feign.Request;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientRetryableException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClient;
import io.github.surezzzzzz.sdk.feedback.client.feign.configuration.SimpleFeedbackClientFeignJakartaAutoConfiguration.JakartaFeedbackFeignClient;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class FeedbackFeignClientContractTest {

    @Test
    void mapsWireResponsesAndMultipartPartsThroughCoreContract() {
        RecordingContract contract = new RecordingContract();
        JakartaFeedbackFeignClient client = new JakartaFeedbackFeignClient(contract);

        assertEquals("submission-1", client.createSubmission(submission(),
                Collections.singletonList(new FeedbackAttachmentPart("readme.txt", "text/plain",
                        "hello".getBytes(StandardCharsets.UTF_8))), "idem-1", "corr-1").getId());
        assertEquals("idem-1", contract.idempotencyKey);
        assertEquals("corr-1", contract.correlationId);
        assertNotNull(contract.parts.getFirst("submission"));
        assertNotNull(contract.parts.getFirst("attachments"));
        assertEquals("submission-1", client.getSubmission("submission-1").getId());
        assertEquals("submission-1", client.handleSubmission("submission-1",
                Collections.<String, Object>singletonMap("status", "resolved"), null).getId());
        log.info("验证 Jakarta Feign multipart 与 core 快照映射");
    }

    @Test
    void preservesDownloadHeaders() {
        RecordingContract contract = new RecordingContract();
        JakartaFeedbackFeignClient client = new JakartaFeedbackFeignClient(contract);
        assertEquals("text/plain", client.downloadSubmissionAttachment("s-1", "a-1").getContentType());
        assertEquals("attachment; filename=readme.txt", client.downloadSubmissionAttachment("s-1", "a-1")
                .getContentDisposition());
        log.info("验证 Jakarta Feign 下载响应保留媒体类型和文件名头");
    }

    @Test
    void classifiesFeignFailuresAndValidatesArguments() {
        FeedbackClientValidationException validation = assertThrows(FeedbackClientValidationException.class,
                () -> new JakartaFeedbackFeignClient(new RecordingContract()).getSubmission(" "));
        assertEquals("path", validation.getParameter());

        RecordingContract serverError = new RecordingContract();
        serverError.failure = FeignException.errorStatus("Feedback", response(503));
        assertThrows(FeedbackClientRetryableException.class,
                () -> new JakartaFeedbackFeignClient(serverError).getSubmission("s-1"));

        RecordingContract clientError = new RecordingContract();
        clientError.failure = FeignException.errorStatus("Feedback", response(403));
        assertThrows(FeedbackClientProtocolException.class,
                () -> new JakartaFeedbackFeignClient(clientError).getSubmission("s-1"));
        log.info("验证 Feign 5xx/4xx 异常分类和参数校验");
    }

    private static Map<String, Object> submission() {
        Map<String, Object> attachment = new LinkedHashMap<String, Object>();
        attachment.put("id", "a-1");
        attachment.put("originalFilename", "readme.txt");
        attachment.put("contentType", "text/plain");
        attachment.put("sizeBytes", 5);
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", "submission-1");
        value.put("applicationCode", "app-1");
        value.put("kind", "proactive");
        value.put("type", "bug");
        value.put("score", 8);
        value.put("content", "content");
        value.put("status", "submitted");
        value.put("rowVersion", 0);
        value.put("attachments", Collections.singletonList(attachment));
        value.put("createdTime", "2026-10-28T10:00:00Z");
        return value;
    }

    private static feign.Response response(int status) {
        Request request = Request.create(Request.HttpMethod.GET,
                "http://localhost/api/feedback/submissions/s-1",
                Collections.<String, java.util.Collection<String>>emptyMap(), null,
                StandardCharsets.UTF_8, null);
        return feign.Response.builder().status(status).reason("test")
                .request(request)
                .headers(Collections.<String, java.util.Collection<String>>emptyMap())
                .body(new byte[0]).build();
    }

    private static final class RecordingContract implements FeedbackFeignClient {
        private String idempotencyKey;
        private String correlationId;
        private MultiValueMap<String, Object> parts;
        private RuntimeException failure;

        @Override
        public Map<String, Object> createSubmission(String idempotencyKey, String correlationId,
                                                     MultiValueMap<String, Object> parts) {
            failIfNecessary();
            this.idempotencyKey = idempotencyKey;
            this.correlationId = correlationId;
            this.parts = parts;
            return submission();
        }

        @Override public Map<String, Object> listSubmissions(Map<String, Object> query) { failIfNecessary(); return page(); }
        @Override public Map<String, Object> getSubmission(String id) { failIfNecessary(); return submission(); }
        @Override public Map<String, Object> handleSubmission(String id, String correlationId,
                                                               Map<String, Object> handling) { failIfNecessary(); return submission(); }
        @Override public ResponseEntity<byte[]> downloadSubmissionAttachment(String id, String attachmentId) {
            failIfNecessary();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "text/plain");
            headers.add("Content-Disposition", "attachment; filename=readme.txt");
            return new ResponseEntity<byte[]>("hello".getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
        }
        @Override public Map<String, Object> listMySubmissions(Map<String, Object> query) { failIfNecessary(); return page(); }
        @Override public Map<String, Object> getMySubmission(String id) { failIfNecessary(); return submission(); }
        @Override public ResponseEntity<byte[]> downloadMySubmissionAttachment(String id, String attachmentId) {
            return downloadSubmissionAttachment(id, attachmentId);
        }
        @Override public Map<String, Object> getSubmissionHints() { failIfNecessary(); return Collections.singletonMap("applications", Collections.emptyList()); }
        @Override public List<Map<String, Object>> listSelectableApplications() { failIfNecessary(); return Collections.emptyList(); }
        @Override public List<Map<String, Object>> listApplications() { failIfNecessary(); return Collections.emptyList(); }
        @Override public Map<String, Object> createApplication(String correlationId, Map<String, Object> application) { failIfNecessary(); return application; }
        @Override public Map<String, Object> updateApplication(String applicationCode, String correlationId, Map<String, Object> application) { failIfNecessary(); return application; }

        private Map<String, Object> page() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("items", Collections.singletonList(submission()));
            result.put("total", 1);
            result.put("page", 1);
            result.put("size", 20);
            return result;
        }

        private void failIfNecessary() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
