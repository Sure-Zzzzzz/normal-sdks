package io.github.surezzzzzz.sdk.feedback.client.feign;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.feedback.client.constant.FeedbackClientConstant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import java.util.List;
import java.util.Map;

/**
 * Feedback HTTP wire 契约。返回值保持公开 JSON Map，避免 Jackson 直接构造 core 快照模型。
 */
@AkskClientFeignClient(name = "feedback",
        url = "${io.github.surezzzzzz.sdk.feedback.client.base-url}",
        path = FeedbackClientConstant.API_BASE_PATH)
public interface FeedbackFeignClient {

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> createSubmission(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestPart("submission") org.springframework.web.multipart.MultipartFile submission,
            @RequestPart(value = "attachments", required = false) List<org.springframework.web.multipart.MultipartFile> attachments);

    @GetMapping("/submissions")
    Map<String, Object> listSubmissions(@RequestParam Map<String, ?> query);

    @GetMapping("/submissions/{id}")
    Map<String, Object> getSubmission(@PathVariable("id") String id);

    @PutMapping("/submissions/{id}/handling")
    Map<String, Object> handleSubmission(@PathVariable("id") String id,
                                         @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                         @RequestBody Map<String, Object> handling);

    @GetMapping("/submissions/{id}/attachments/{attachmentId}")
    ResponseEntity<byte[]> downloadSubmissionAttachment(@PathVariable("id") String id,
                                                        @PathVariable("attachmentId") String attachmentId);

    @GetMapping("/me/submissions")
    Map<String, Object> listMySubmissions(@RequestParam Map<String, ?> query);

    @GetMapping("/me/submissions/{id}")
    Map<String, Object> getMySubmission(@PathVariable("id") String id);

    @GetMapping("/me/submissions/{id}/attachments/{attachmentId}")
    ResponseEntity<byte[]> downloadMySubmissionAttachment(@PathVariable("id") String id,
                                                          @PathVariable("attachmentId") String attachmentId);

    @GetMapping("/submission-hints")
    Map<String, Object> getSubmissionHints();

    @GetMapping("/selectable-applications")
    List<Map<String, Object>> listSelectableApplications();

    @GetMapping("/applications")
    List<Map<String, Object>> listApplications();

    @PostMapping("/applications")
    Map<String, Object> createApplication(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> application);

    @PutMapping("/applications/{applicationCode}")
    Map<String, Object> updateApplication(
            @PathVariable("applicationCode") String applicationCode,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> application);
}
