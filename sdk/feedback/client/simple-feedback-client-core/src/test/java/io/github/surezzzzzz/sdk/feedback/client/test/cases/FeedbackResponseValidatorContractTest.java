package io.github.surezzzzzz.sdk.feedback.client.test.cases;

import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmissionHint;
import io.github.surezzzzzz.sdk.feedback.client.support.FeedbackResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 校验客户端模型与 OpenAPI 顶层响应结构一致，避免误要求不存在的包装字段。
 */
@Slf4j
class FeedbackResponseValidatorContractTest {

    @Test
    void acceptsSubmissionResponseWithoutWrapperField() {
        Map<String, Object> response = submission("submission-1");

        log.info("校验反馈响应: {}", response.get("id"));
        FeedbackSubmission result = FeedbackResponseValidator.submission(response);

        log.info("反馈响应校验成功: {}", result.getId());
        assertEquals("submission-1", result.getId());
    }

    @Test
    void rejectsSubmissionPageWithMalformedItem() {
        Map<String, Object> page = page(Collections.<Map<String, Object>>singletonList(
                Collections.<String, Object>singletonMap("id", "incomplete")));

        log.info("校验含异常条目的分页响应");
        assertThrows(FeedbackClientProtocolException.class, () -> FeedbackResponseValidator.page(page));
    }

    @Test
    void acceptsNullableContactEmailWhenKeyIsPresent() {
        Map<String, Object> application = new LinkedHashMap<String, Object>();
        application.put("applicationCode", "sample-app");
        Map<String, Object> hint = new LinkedHashMap<String, Object>();
        hint.put("applications", Collections.<Map<String, Object>>singletonList(application));
        hint.put("contactEmail", null);

        log.info("校验无邮箱的提示响应");
        FeedbackSubmissionHint result = FeedbackResponseValidator.hint(hint);

        assertNull(result.getContactEmail());
        assertEquals("sample-app", result.getApplications().get(0).getApplicationCode());
    }

    @Test
    void keepsHintCollectionsAndApplicationFieldsAsSnapshots() {
        Map<String, Object> application = new LinkedHashMap<String, Object>();
        application.put("applicationCode", "sample-app");
        List<Map<String, Object>> rawApplications = new ArrayList<Map<String, Object>>();
        rawApplications.add(application);
        Map<String, Object> hint = new LinkedHashMap<String, Object>();
        hint.put("applications", rawApplications);
        hint.put("contactEmail", null);

        FeedbackSubmissionHint result = FeedbackResponseValidator.hint(hint);
        rawApplications.clear();
        application.put("applicationCode", "changed");

        assertEquals(1, result.getApplications().size());
        assertEquals("sample-app", result.getApplications().get(0).getApplicationCode());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getApplications().get(0).asMap().put("displayName", "changed"));
    }

    @Test
    void rejectsSubmissionWithMalformedAttachmentMetadata() {
        Map<String, Object> response = submission("submission-attachment");
        response.put("attachments", Collections.<Map<String, Object>>singletonList(
                Collections.<String, Object>singletonMap("id", "attachment-1")));

        assertThrows(FeedbackClientProtocolException.class,
                () -> FeedbackResponseValidator.submission(response));
    }

    @Test
    void acceptsApplicationResponseWithoutWrapperField() {
        Map<String, Object> application = application("sample-app");

        log.info("校验注册表响应: {}", application.get("applicationCode"));
        assertEquals(application, FeedbackResponseValidator.application(application));
        assertEquals(1, FeedbackResponseValidator.applicationList(
                Collections.<Map<String, Object>>singletonList(application)).size());
    }

    @Test
    void keepsApplicationResponseAsImmutableSnapshot() {
        Map<String, Object> application = application("sample-app");

        Map<String, Object> result = FeedbackResponseValidator.application(application);

        assertThrows(UnsupportedOperationException.class,
                () -> result.put("displayName", "changed"));
    }

    private static Map<String, Object> submission(String id) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("id", id);
        response.put("applicationCode", "sample-app");
        response.put("kind", "proactive");
        response.put("type", "bug");
        response.put("score", 8);
        response.put("content", "sample content");
        response.put("status", "submitted");
        response.put("rowVersion", 0);
        response.put("attachments", new ArrayList<Map<String, Object>>());
        response.put("createdTime", "2026-01-01T00:00:00Z");
        return response;
    }

    private static Map<String, Object> page(List<Map<String, Object>> items) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("items", items);
        response.put("total", 1);
        response.put("page", 1);
        response.put("size", 20);
        return response;
    }

    private static Map<String, Object> application(String code) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("applicationCode", code);
        response.put("displayName", "Sample application");
        response.put("enabled", true);
        response.put("rowVersion", 0);
        response.put("createdTime", "2026-01-01T00:00:00Z");
        response.put("updatedTime", "2026-01-01T00:00:00Z");
        return response;
    }
}
