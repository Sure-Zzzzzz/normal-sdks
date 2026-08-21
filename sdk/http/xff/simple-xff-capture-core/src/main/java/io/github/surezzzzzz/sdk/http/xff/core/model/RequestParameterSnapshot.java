package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.*;

/**
 * 单个请求参数来源的不可变快照。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = "values")
public final class RequestParameterSnapshot {

    private final RequestDataCaptureStatus status;
    private final Map<String, List<String>> values;

    /**
     * 创建请求参数快照。
     *
     * @param status 状态
     * @param values 参数值
     */
    public RequestParameterSnapshot(RequestDataCaptureStatus status,
                                    Map<String, List<String>> values) {
        if (status == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "status"));
        }
        if (values == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "values"));
        }
        this.status = status;
        this.values = immutableCopy(values);
        validateState();
    }

    private Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                        String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "values"));
            }
            List<String> valuesCopy = new ArrayList<>(entry.getValue().size());
            for (String value : entry.getValue()) {
                if (value == null) {
                    throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                            String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "values"));
                }
                valuesCopy.add(value);
            }
            copy.put(entry.getKey(), Collections.unmodifiableList(valuesCopy));
        }
        return Collections.unmodifiableMap(copy);
    }

    private void validateState() {
        if (status != RequestDataCaptureStatus.CAPTURED && !values.isEmpty()) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "非 CAPTURED 参数状态不能携带参数值"));
        }
        if (status == RequestDataCaptureStatus.CAPTURED && values.isEmpty()) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "CAPTURED 参数状态必须携带参数值"));
        }
    }
}
