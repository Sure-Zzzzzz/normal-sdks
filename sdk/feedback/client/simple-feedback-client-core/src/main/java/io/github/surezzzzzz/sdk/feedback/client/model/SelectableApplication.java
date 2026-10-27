package io.github.surezzzzzz.sdk.feedback.client.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 提交侧可选应用。 */
public final class SelectableApplication {
    private final Map<String, Object> fields;

    public SelectableApplication(Map<String, Object> fields) {
        this.fields = fields == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fields));
    }
    public Map<String, Object> asMap() { return fields; }
    public String getApplicationCode() {
        Object value = fields == null ? null : fields.get("applicationCode");
        return value == null ? null : String.valueOf(value);
    }
}
