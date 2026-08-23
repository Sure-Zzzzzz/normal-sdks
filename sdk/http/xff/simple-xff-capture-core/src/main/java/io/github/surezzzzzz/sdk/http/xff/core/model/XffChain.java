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
 * 应用通过 Servlet 容器看到的 XFF 不可变快照。
 *
 * <p>链元素只代表采集执行时 Servlet 容器暴露的 Header 事实，不代表可信客户端 IP。
 * 如果更早的容器组件已经消费 Header，快照不承诺还原 LB、Ingress 或代理最初传入的值。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"rawHeaderList", "rawList"})
public final class XffChain {

    /**
     * Servlet 容器是否暴露至少一个 XFF Header 值。
     */
    private final boolean present;

    /**
     * 采集执行时 Servlet 容器按枚举顺序暴露的原始 Header 值列表。
     * 该列表保留同名 Header 的边界，不保证是入口最初收到的完整 Header。
     */
    private final List<String> rawHeaderList;

    /**
     * 按逗号机械拆分并移除两侧 HTTP 可选空白后的有序链。
     */
    private final List<String> rawList;

    /**
     * 创建不可变 XFF 快照。
     *
     * @param present       Header 是否存在
     * @param rawHeaderList 原始 Header 值列表
     * @param rawList       拆分后的有序链
     */
    public XffChain(boolean present, List<String> rawHeaderList, List<String> rawList) {
        this.rawHeaderList = immutableCopy(rawHeaderList,
                SimpleXffCaptureCoreConstant.FIELD_RAW_HEADER_LIST);
        this.rawList = immutableCopy(rawList, SimpleXffCaptureCoreConstant.FIELD_RAW_LIST);
        validateState(present, this.rawHeaderList, this.rawList);
        this.present = present;
    }

    private static List<String> immutableCopy(List<String> source, String name) {
        if (source == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, name));
        }
        List<String> copy = new ArrayList<>(source.size());
        for (String value : source) {
            if (value == null) {
                throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                        String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                                String.format(SimpleXffCaptureCoreConstant.DETAIL_COLLECTION_CONTAINS_NULL, name)));
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private static void validateState(boolean present, List<String> rawHeaderList, List<String> rawList) {
        if (!present && (!rawHeaderList.isEmpty() || !rawList.isEmpty())) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            SimpleXffCaptureCoreConstant.DETAIL_ABSENT_XFF_CHAIN_STATE_INVALID));
        }
        if (present && (rawHeaderList.isEmpty() || rawList.isEmpty())) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            SimpleXffCaptureCoreConstant.DETAIL_PRESENT_XFF_CHAIN_STATE_INVALID));
        }
    }
}
