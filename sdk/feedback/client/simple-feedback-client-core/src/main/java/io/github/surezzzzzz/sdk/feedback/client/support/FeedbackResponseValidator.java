package io.github.surezzzzzz.sdk.feedback.client.support;

import io.github.surezzzzzz.sdk.feedback.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.client.constant.FeedbackClientConstant;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackPage;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmissionHint;
import io.github.surezzzzzz.sdk.feedback.client.model.SelectableApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只校验公开响应的必需结构，拒绝空对象，不复用服务端模型。 */
public final class FeedbackResponseValidator {
    private FeedbackResponseValidator() { throw new UnsupportedOperationException("Utility class"); }

    public static FeedbackSubmission submission(Map<String, Object> value) {
        require(value, "id");
        require(value, "applicationCode");
        require(value, "kind");
        require(value, "type");
        require(value, "score");
        require(value, "content");
        require(value, "status");
        require(value, "rowVersion");
        require(value, "attachments");
        require(value, "createdTime");
        Object rawAttachments = value.get("attachments");
        if (!(rawAttachments instanceof List)) {
            throw invalid("attachments");
        }
        for (Object attachment : (List<?>) rawAttachments) {
            if (!(attachment instanceof Map)) {
                throw invalid("attachments");
            }
            attachment((Map<String, Object>) attachment);
        }
        return new FeedbackSubmission(value);
    }

    public static FeedbackPage page(Map<String, Object> value) {
        require(value, "items");
        require(value, "total");
        require(value, "page");
        require(value, "size");
        Object raw = value.get("items");
        if (!(raw instanceof List)) {
            throw invalid("items");
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                throw invalid("items");
            }
            Map<String, Object> submission = (Map<String, Object>) item;
            submission(submission);
            items.add(submission);
        }
        return new FeedbackPage(items, number(value, "total").longValue(), number(value, "page").intValue(),
                number(value, "size").intValue());
    }

    public static FeedbackSubmissionHint hint(Map<String, Object> value) {
        require(value, "applications");
        requireKey(value, "contactEmail");
        Object applications = value.get("applications");
        if (!(applications instanceof List)) {
            throw invalid("applications");
        }
        List<SelectableApplication> result = new ArrayList<SelectableApplication>();
        for (Object item : (List<?>) applications) {
            if (!(item instanceof Map)) {
                throw invalid("applications");
            }
            Map<String, Object> application = (Map<String, Object>) item;
            require(application, "applicationCode");
            result.add(new SelectableApplication(application));
        }
        return new FeedbackSubmissionHint(result, value.get("contactEmail") == null
                ? null : String.valueOf(value.get("contactEmail")));
    }

    public static List<SelectableApplication> applications(List<?> values) {
        if (values == null) {
            throw invalid("applications");
        }
        List<SelectableApplication> result = new ArrayList<SelectableApplication>();
        for (Object item : values) {
            if (!(item instanceof Map)) {
                throw invalid("applications");
            }
            Map<String, Object> application = (Map<String, Object>) item;
            require(application, "applicationCode");
            result.add(new SelectableApplication(application));
        }
        return Collections.unmodifiableList(result);
    }

    public static Map<String, Object> application(Map<String, Object> value) {
        require(value, "applicationCode");
        require(value, "displayName");
        require(value, "enabled");
        require(value, "rowVersion");
        require(value, "createdTime");
        require(value, "updatedTime");
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(value));
    }

    private static Map<String, Object> attachment(Map<String, Object> value) {
        require(value, "id");
        require(value, "originalFilename");
        require(value, "contentType");
        require(value, "sizeBytes");
        number(value, "sizeBytes");
        return value;
    }

    public static List<Map<String, Object>> applicationList(List<?> values) {
        if (values == null) {
            throw invalid("applications");
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : values) {
            if (!(item instanceof Map)) {
                throw invalid("applications");
            }
            result.add(application((Map<String, Object>) item));
        }
        return Collections.unmodifiableList(result);
    }

    private static void require(Map<String, Object> value, String name) {
        if (value == null || !value.containsKey(name) || value.get(name) == null) {
            throw invalid(name);
        }
    }

    private static void requireKey(Map<String, Object> value, String name) {
        if (value == null || !value.containsKey(name)) {
            throw invalid(name);
        }
    }

    private static Number number(Map<String, Object> value, String name) {
        Object raw = value.get(name);
        if (!(raw instanceof Number)) {
            throw invalid(name);
        }
        return (Number) raw;
    }

    private static FeedbackClientProtocolException invalid(String field) {
        return new FeedbackClientProtocolException(FeedbackClientConstant.SUCCESS_STATUS,
                String.format(ErrorMessage.RESPONSE_FIELD_INVALID, field), null);
    }
}
