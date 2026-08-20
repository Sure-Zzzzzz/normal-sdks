package io.github.surezzzzzz.sdk.audit.http.xff.context;

import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorCode;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.exception.XffCaptureAuditValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XFF Capture 可选审计上下文。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"requestId", "traceId", "extensions"})
public final class XffCaptureAuditContext {

    /**
     * 请求标识。
     */
    private final String requestId;

    /**
     * 链路标识。
     */
    private final String traceId;

    /**
     * 业务扩展字段。
     */
    private final Map<String, String> extensions;

    /**
     * 创建不带业务扩展的可选审计上下文。
     *
     * @param requestId 请求标识
     * @param traceId   链路标识
     */
    public XffCaptureAuditContext(String requestId, String traceId) {
        this(requestId, traceId, Collections.<String, String>emptyMap());
    }

    /**
     * 创建带业务扩展的可选审计上下文。
     *
     * @param requestId  请求标识
     * @param traceId    链路标识
     * @param extensions 业务扩展字段
     */
    public XffCaptureAuditContext(String requestId, String traceId,
                                  Map<String, String> extensions) {
        this.requestId = normalize(requestId);
        this.traceId = normalize(traceId);
        this.extensions = immutableCopy(extensions);
    }

    private Map<String, String> immutableCopy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (key == null || value == null) {
                throw stateException(String.format(
                        SimpleXffCaptureAuditConstant.DETAIL_EXTENSION_TEXT_INVALID,
                        key == null ? "键" : key));
            }
            if (copy.containsKey(key)) {
                throw stateException(String.format(
                        SimpleXffCaptureAuditConstant.DETAIL_EXTENSION_DUPLICATED_KEY,
                        key));
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private XffCaptureAuditValidationException stateException(String detail) {
        return new XffCaptureAuditValidationException(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID,
                String.format(ErrorMessage.AUDIT_DOCUMENT_STATE_INVALID, detail));
    }
}
