package io.github.surezzzzzz.sdk.feedback.client.model;

import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 已校验的 Feedback 提交响应快照。 */
public final class FeedbackSubmission {

    private final Map<String, Object> fields;

    public FeedbackSubmission(Map<String, Object> fields) {
        if (fields == null) {
            throw new FeedbackClientValidationException("fields");
        }
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fields));
    }

    public Map<String, Object> asMap() {
        return fields;
    }

    public Object get(String name) {
        return fields.get(name);
    }

    public String getId() {
        return string("id");
    }

    public int getRowVersion() {
        Object value = get("rowVersion");
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private String string(String name) {
        Object value = get(name);
        return value == null ? null : String.valueOf(value);
    }
}
