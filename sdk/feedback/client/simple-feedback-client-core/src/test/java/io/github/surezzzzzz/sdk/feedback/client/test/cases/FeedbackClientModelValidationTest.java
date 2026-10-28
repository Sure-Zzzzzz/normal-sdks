package io.github.surezzzzzz.sdk.feedback.client.test.cases;

import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.support.FeedbackResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class FeedbackClientModelValidationTest {

    @Test
    void rejectsNullSubmissionFieldsWithClientValidationException() {
        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackSubmission(null));

        log.info("空反馈字段被客户端校验拒绝，参数={}", exception.getParameter());
        assertEquals("fields", exception.getParameter());
    }

    @Test
    void rejectsEachInvalidAttachmentFieldWithClientValidationException() {
        log.info("开始校验附件文件名、媒体类型和内容的非法输入");
        assertEquals("filename", assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackAttachmentPart(" ", "text/plain", new byte[] {1})).getParameter());
        assertEquals("contentType", assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackAttachmentPart("a.txt", " ", new byte[] {1})).getParameter());
        assertEquals("content", assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackAttachmentPart("a.txt", "text/plain", null)).getParameter());
    }

    @Test
    void keepsSubmissionFieldsIndependentFromCallerMap() {
        FeedbackSubmission submission = new FeedbackSubmission(Collections.<String, Object>singletonMap("id", "f-1"));

        log.info("反馈快照保留公开标识={}", submission.getId());
        assertEquals("f-1", submission.getId());
    }

    @Test
    void rejectsSubmissionResponseMissingOpenApiRequiredField() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("id", "f-1");
        response.put("applicationCode", "portal");
        response.put("status", "submitted");
        response.put("rowVersion", 0);
        response.put("attachments", Collections.emptyList());

        FeedbackClientProtocolException exception = assertThrows(FeedbackClientProtocolException.class,
                () -> FeedbackResponseValidator.submission(response));

        log.info("缺少公开字段的反馈响应被拒绝，消息={}", exception.getMessage());
        assertEquals("Feedback response field invalid: kind", exception.getMessage());
    }

    @Test
    void acceptsCompleteTopLevelSubmissionResponse() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("id", "f-1");
        response.put("applicationCode", "portal");
        response.put("kind", "proactive");
        response.put("type", "bug");
        response.put("score", 8);
        response.put("content", "content");
        response.put("status", "submitted");
        response.put("rowVersion", 0);
        response.put("attachments", Collections.emptyList());
        response.put("createdTime", "2026-10-24T12:00:00+08:00");

        String id = FeedbackResponseValidator.submission(response).getId();
        log.info("完整反馈响应通过校验，标识={}", id);
        assertEquals("f-1", id);
    }

    @Test
    void validatesEachSubmissionInPageItems() {
        Map<String, Object> page = new HashMap<String, Object>();
        page.put("items", Collections.<Map<String, Object>>singletonList(
                Collections.<String, Object>singletonMap("id", "f-1")));
        page.put("total", 1);
        page.put("page", 1);
        page.put("size", 20);

        FeedbackClientProtocolException exception = assertThrows(FeedbackClientProtocolException.class,
                () -> FeedbackResponseValidator.page(page));

        log.info("分页内不完整反馈行被拒绝，消息={}", exception.getMessage());
        assertEquals("Feedback response field invalid: applicationCode", exception.getMessage());
    }
}
