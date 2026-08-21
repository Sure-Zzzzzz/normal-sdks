package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 请求体不可变快照。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = "text")
public final class RequestBodySnapshot {

    private final RequestBodyCaptureStatus status;
    private final String contentType;
    private final Long declaredContentLength;
    private final long capturedByteCount;
    private final String text;

    /**
     * 创建请求体快照。
     *
     * @param status                状态
     * @param contentType           Content-Type
     * @param declaredContentLength 声明长度
     * @param capturedByteCount     实际保留字节数
     * @param text                  保留的 UTF-8 文本
     */
    public RequestBodySnapshot(RequestBodyCaptureStatus status, String contentType,
                               Long declaredContentLength, long capturedByteCount, String text) {
        if (status == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "status"));
        }
        if (declaredContentLength != null && declaredContentLength < 0L) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "声明 Body 长度不能为负数"));
        }
        if (capturedByteCount < 0L) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "实际保留 Body 长度不能为负数"));
        }
        this.status = status;
        this.contentType = contentType;
        this.declaredContentLength = declaredContentLength;
        this.capturedByteCount = capturedByteCount;
        this.text = text;
        validateState();
    }

    private void validateState() {
        boolean textState = status == RequestBodyCaptureStatus.CAPTURED
                || status == RequestBodyCaptureStatus.TRUNCATED;
        if (textState && text == null) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "已读取 Body 状态必须携带文本"));
        }
        if (!textState && text != null) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "未读取 Body 状态不能携带文本"));
        }
        if (!textState && capturedByteCount != 0L) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "未读取 Body 状态不能携带保留字节"));
        }
    }
}
