package io.github.surezzzzzz.sdk.feedback.client.feign;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** Feedback 公开 HTTP wire 契约；返回值保持 Map，避免 Feign 直接绑定 core 快照。 */
@AkskClientFeignClient(name = "feedback",
        url = "${io.github.surezzzzzz.sdk.feedback.client.base-url}")
public interface FeedbackFeignClient {

    @PostMapping(value = "/api/feedback/submissions", consumes = "multipart/form-data")
    Map<String, Object> createSubmission(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody MultiValueMap<String, Object> parts);

    @GetMapping("/api/feedback/submissions")
    Map<String, Object> listSubmissions(@RequestParam Map<String, Object> query);

    @GetMapping("/api/feedback/submissions/{id}")
    Map<String, Object> getSubmission(@PathVariable("id") String id);

    @PutMapping("/api/feedback/submissions/{id}/handling")
    Map<String, Object> handleSubmission(@PathVariable("id") String id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> handling);

    @GetMapping("/api/feedback/submissions/{id}/attachments/{attachmentId}")
    ResponseEntity<byte[]> downloadSubmissionAttachment(@PathVariable("id") String id,
            @PathVariable("attachmentId") String attachmentId);

    @GetMapping("/api/feedback/me/submissions")
    Map<String, Object> listMySubmissions(@RequestParam Map<String, Object> query);

    @GetMapping("/api/feedback/me/submissions/{id}")
    Map<String, Object> getMySubmission(@PathVariable("id") String id);

    @GetMapping("/api/feedback/me/submissions/{id}/attachments/{attachmentId}")
    ResponseEntity<byte[]> downloadMySubmissionAttachment(@PathVariable("id") String id,
            @PathVariable("attachmentId") String attachmentId);

    @GetMapping("/api/feedback/submission-hints")
    Map<String, Object> getSubmissionHints();

    @GetMapping("/api/feedback/selectable-applications")
    List<Map<String, Object>> listSelectableApplications();

    @GetMapping("/api/feedback/applications")
    List<Map<String, Object>> listApplications();

    @PostMapping("/api/feedback/applications")
    Map<String, Object> createApplication(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> application);

    @PutMapping("/api/feedback/applications/{applicationCode}")
    Map<String, Object> updateApplication(@PathVariable("applicationCode") String applicationCode,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> application);
}
