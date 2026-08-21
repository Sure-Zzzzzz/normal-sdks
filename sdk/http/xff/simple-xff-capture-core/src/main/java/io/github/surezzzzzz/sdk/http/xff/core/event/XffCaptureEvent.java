package io.github.surezzzzzz.sdk.http.xff.core.event;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.ForwardedContext;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffCaptureSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * XFF 采集事件。
 *
 * <p>事件不持有 Servlet 生命周期对象，也不推导可信客户端 IP。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"requestUri", "applicationRawRemoteAddress", "snapshot"})
public final class XffCaptureEvent {

    /**
     * 唯一事件标识。
     */
    private final String eventId;

    /**
     * 采集时间。
     */
    private final Instant occurredAt;

    /**
     * HTTP 方法。
     */
    private final String requestMethod;

    /**
     * 不包含查询参数的请求 URI。
     */
    private final String requestUri;

    /**
     * Capture Filter 执行时应用通过 getRemoteAddr() 看到的原始远端地址，不属于 XFF 链。
     */
    private final String applicationRawRemoteAddress;

    /**
     * 不可变完整采集快照。
     */
    private final XffCaptureSnapshot snapshot;

    /**
     * 请求参数与请求体快照。
     */
    private final RequestDataSnapshot requestData;

    /**
     * 创建兼容旧版本的 XFF 采集事件。
     *
     * @param eventId                     唯一事件标识
     * @param occurredAt                  采集时间
     * @param requestMethod               HTTP 方法
     * @param requestUri                  请求 URI
     * @param applicationRawRemoteAddress 应用可见原始远端地址，允许为空
     * @param snapshot                    完整采集快照
     */
    public XffCaptureEvent(String eventId, Instant occurredAt, String requestMethod, String requestUri,
                           String applicationRawRemoteAddress, XffCaptureSnapshot snapshot) {
        this(eventId, occurredAt, requestMethod, requestUri, applicationRawRemoteAddress, snapshot,
                RequestDataSnapshot.disabled());
    }

    /**
     * 创建包含完整请求数据的 XFF 采集事件。
     *
     * @param eventId                     唯一事件标识
     * @param occurredAt                  采集时间
     * @param requestMethod               HTTP 方法
     * @param requestUri                  请求 URI
     * @param applicationRawRemoteAddress 应用可见原始远端地址，允许为空
     * @param snapshot                    完整采集快照
     * @param requestData                 请求数据快照
     */
    public XffCaptureEvent(String eventId, Instant occurredAt, String requestMethod, String requestUri,
                           String applicationRawRemoteAddress, XffCaptureSnapshot snapshot,
                           RequestDataSnapshot requestData) {
        this.eventId = requireText(eventId, SimpleXffCaptureCoreConstant.FIELD_EVENT_ID);
        if (occurredAt == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureCoreConstant.FIELD_OCCURRED_AT));
        }
        this.occurredAt = occurredAt;
        this.requestMethod = requireText(requestMethod, SimpleXffCaptureCoreConstant.FIELD_REQUEST_METHOD);
        this.requestUri = requireText(requestUri, SimpleXffCaptureCoreConstant.FIELD_REQUEST_URI);
        this.applicationRawRemoteAddress = applicationRawRemoteAddress;
        if (snapshot == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureCoreConstant.FIELD_CAPTURE_SNAPSHOT));
        }
        this.snapshot = snapshot;
        if (requestData == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "requestData"));
        }
        this.requestData = requestData;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, name));
        }
        return value;
    }

    /**
     * 获取 XFF 链快照。
     *
     * @return XFF 链快照
     */
    public XffChain getXffChain() {
        return snapshot.getXffChain();
    }

    /**
     * 获取固定入口转发上下文。
     *
     * @return 入口转发上下文
     */
    public ForwardedContext getForwardedContext() {
        return snapshot.getForwardedContext();
    }
}
