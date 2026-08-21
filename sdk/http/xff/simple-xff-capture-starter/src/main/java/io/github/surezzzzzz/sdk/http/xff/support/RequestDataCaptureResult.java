package io.github.surezzzzzz.sdk.http.xff.support;

import io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot;
import lombok.Getter;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求数据准备结果。
 *
 * @author surezzzzzz
 */
@Getter
public final class RequestDataCaptureResult {

    private final HttpServletRequest request;
    private final RequestDataSnapshot snapshot;

    /**
     * 创建准备结果。
     *
     * @param request  继续传递的请求
     * @param snapshot 请求数据快照
     */
    public RequestDataCaptureResult(HttpServletRequest request, RequestDataSnapshot snapshot) {
        this.request = request;
        this.snapshot = snapshot;
    }
}
