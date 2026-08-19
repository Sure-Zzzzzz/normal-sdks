package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 单次请求的完整 XFF Capture 快照。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"xffChain", "forwardedContext"})
public final class XffCaptureSnapshot {

    /**
     * XFF 链快照。
     */
    private final XffChain xffChain;

    /**
     * 入口转发上下文。
     */
    private final ForwardedContext forwardedContext;

    /**
     * 创建完整采集快照。
     *
     * @param xffChain         XFF 链快照
     * @param forwardedContext 入口转发上下文
     */
    public XffCaptureSnapshot(XffChain xffChain, ForwardedContext forwardedContext) {
        if (xffChain == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureCoreConstant.FIELD_XFF_CHAIN));
        }
        if (forwardedContext == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureCoreConstant.FIELD_FORWARDED_CONTEXT));
        }
        this.xffChain = xffChain;
        this.forwardedContext = forwardedContext;
    }
}
