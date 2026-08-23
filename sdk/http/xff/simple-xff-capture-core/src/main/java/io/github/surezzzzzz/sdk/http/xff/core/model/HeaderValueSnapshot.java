package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个 HTTP Header 的不可变原始值快照。
 *
 * <p>快照只反映采集执行时 Servlet 容器暴露的值，不校验、不合并、不替代
 * 其他 Header，也不解释可信性；更早组件消费的 Header 不会由此快照恢复。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = "rawValueList")
public final class HeaderValueSnapshot {

    /**
     * Servlet 容器是否暴露至少一个同名 Header 值。
     */
    private final boolean present;

    /**
     * Servlet 容器按枚举顺序暴露的原始值列表。
     */
    private final List<String> rawValueList;

    /**
     * 创建 Header 原始值快照。
     *
     * @param present      Header 是否存在
     * @param rawValueList 原始值列表
     */
    public HeaderValueSnapshot(boolean present, List<String> rawValueList) {
        if (rawValueList == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, SimpleXffCaptureCoreConstant.FIELD_RAW_VALUE_LIST));
        }
        List<String> copy = new ArrayList<>(rawValueList.size());
        for (String value : rawValueList) {
            if (value == null) {
                throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                        String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                                String.format(SimpleXffCaptureCoreConstant.DETAIL_COLLECTION_CONTAINS_NULL,
                                        SimpleXffCaptureCoreConstant.FIELD_RAW_VALUE_LIST)));
            }
            copy.add(value);
        }
        if (!present && !copy.isEmpty()) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            SimpleXffCaptureCoreConstant.DETAIL_ABSENT_HEADER_STATE_INVALID));
        }
        if (present && copy.isEmpty()) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            SimpleXffCaptureCoreConstant.DETAIL_PRESENT_HEADER_STATE_INVALID));
        }
        this.present = present;
        this.rawValueList = Collections.unmodifiableList(copy);
    }
}
