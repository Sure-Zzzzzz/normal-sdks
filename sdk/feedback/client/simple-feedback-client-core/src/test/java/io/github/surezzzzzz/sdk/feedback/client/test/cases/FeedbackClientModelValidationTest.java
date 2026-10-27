package io.github.surezzzzzz.sdk.feedback.client.test.cases;

import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.support.FeedbackResponseValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedbackClientModelValidationTest {

    @Test
    void rejectsNullSubmissionFieldsWithClientValidationException() {
        FeedbackClientValidationException exception = assertThrows(FeedbackClientValidationException.class,
                () -> new FeedbackSubmission(null));

        assertEquals("fields", exception.getParameter());
    }

    @Test
    void rejectsEachInvalidAttachmentFieldWithClientValidationException() {
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

        assertEquals("f-1", FeedbackResponseValidator.submission(response).getId());
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

        assertEquals("Feedback response field invalid: applicationCode", exception.getMessage());
    }
}
