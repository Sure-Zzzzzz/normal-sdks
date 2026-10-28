package io.github.surezzzzzz.sdk.feedback.client.feign.test.cases;

import feign.Request;
import feign.Response;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClient;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClientAdapter;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientRetryableException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackDownload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Feign wire 适配器的 core 映射、multipart 和异常分类测试。 */
@Slf4j
class FeedbackFeignClientAdapterTest {

    @Test
    void mapsSubmissionAndKeepsAttachmentMetadata() throws Exception {
        FeedbackFeignClient wire = mock(FeedbackFeignClient.class);
        Map<String, Object> response = submission("submission-1");
        when(wire.createSubmission(any(String.class), any(String.class), any(MultipartFile.class), anyList()))
                .thenReturn(response);
        FeedbackFeignClientAdapter adapter = new FeedbackFeignClientAdapter(wire);

        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("applicationCode", "demo");
        FeedbackAttachmentPart part = new FeedbackAttachmentPart("screen.png", "image/png",
                "png".getBytes(StandardCharsets.UTF_8));
        assertEquals("submission-1", adapter.createSubmission(request,
                Collections.singletonList(part), "idem-1", "corr-1").getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(wire).createSubmission(any(String.class), any(String.class), any(MultipartFile.class), files.capture());
        assertEquals("screen.png", files.getValue().get(0).getOriginalFilename());
        assertEquals("image/png", files.getValue().get(0).getContentType());
    }

    @Test
    void preservesDownloadHeaders() {
        FeedbackFeignClient wire = mock(FeedbackFeignClient.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "image/png");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=screen.png");
        when(wire.downloadMySubmissionAttachment("submission-1", "attachment-1"))
                .thenReturn(new ResponseEntity<byte[]>(new byte[] {1, 2}, headers, HttpStatus.OK));
        FeedbackDownload result = new FeedbackFeignClientAdapter(wire)
                .downloadMySubmissionAttachment("submission-1", "attachment-1");
        assertEquals("image/png", result.getContentType());
        assertEquals("attachment; filename=screen.png", result.getContentDisposition());
        assertEquals(2, result.getContent().length);
    }

    @Test
    void classifiesFeignFailuresAndRejectsInvalidArguments() {
        FeedbackFeignClient wire = mock(FeedbackFeignClient.class);
        when(wire.getSubmission("submission-1")).thenThrow(feignFailure(503));
        FeedbackFeignClientAdapter adapter = new FeedbackFeignClientAdapter(wire);
        assertThrows(FeedbackClientRetryableException.class, () -> adapter.getSubmission("submission-1"));

        when(wire.getSubmission("submission-2")).thenThrow(feignFailure(403));
        assertThrows(FeedbackClientProtocolException.class, () -> adapter.getSubmission("submission-2"));
        assertThrows(FeedbackClientValidationException.class, () -> adapter.getSubmission(" "));
    }

    private static Map<String, Object> submission(String id) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", id);
        value.put("applicationCode", "demo");
        value.put("kind", "proactive");
        value.put("type", "experience");
        value.put("score", 8);
        value.put("content", "content");
        value.put("status", "submitted");
        value.put("rowVersion", 1);
        value.put("attachments", Collections.emptyList());
        value.put("createdTime", "2026-10-28T00:00:00Z");
        return value;
    }

    private static feign.FeignException feignFailure(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/feedback/submissions/x",
                Collections.<String, Collection<String>>emptyMap(), null, StandardCharsets.UTF_8, null);
        return feign.FeignException.errorStatus("feedback", Response.builder()
                .status(status).request(request).headers(Collections.<String, Collection<String>>emptyMap())
                .body("{}", StandardCharsets.UTF_8).build());
    }
}
