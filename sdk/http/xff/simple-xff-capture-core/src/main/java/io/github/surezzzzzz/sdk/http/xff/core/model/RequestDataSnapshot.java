package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 请求参数与请求体的完整不可变快照。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"queryParameters", "formParameters", "body"})
public final class RequestDataSnapshot {

    private final RequestParameterSnapshot queryParameters;
    private final RequestParameterSnapshot formParameters;
    private final RequestBodySnapshot body;

    /**
     * 创建请求数据快照。
     *
     * @param queryParameters 查询参数
     * @param formParameters  表单参数
     * @param body            请求体
     */
    public RequestDataSnapshot(RequestParameterSnapshot queryParameters,
                               RequestParameterSnapshot formParameters,
                               RequestBodySnapshot body) {
        if (queryParameters == null) {
            throw required("queryParameters");
        }
        if (formParameters == null) {
            throw required("formParameters");
        }
        if (body == null) {
            throw required("body");
        }
        this.queryParameters = queryParameters;
        this.formParameters = formParameters;
        this.body = body;
    }

    /**
     * 创建未启用的请求数据快照。
     *
     * @return 未启用快照
     */
    public static RequestDataSnapshot disabled() {
        RequestParameterSnapshot parameters = new RequestParameterSnapshot(
                io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus.DISABLED,
                java.util.Collections.<String, java.util.List<String>>emptyMap());
        RequestBodySnapshot body = new RequestBodySnapshot(
                io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus.DISABLED,
                null, null, 0L, null);
        return new RequestDataSnapshot(parameters, parameters, body);
    }

    private static XffCaptureValidationException required(String field) {
        return new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                String.format(ErrorMessage.REQUIRED_VALUE_MISSING, field));
    }
}
