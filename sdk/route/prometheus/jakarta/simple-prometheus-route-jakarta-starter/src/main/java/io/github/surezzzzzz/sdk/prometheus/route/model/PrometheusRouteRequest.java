package io.github.surezzzzzz.sdk.prometheus.route.model;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Route 受控 HTTP 请求。
 *
 * @author surezzzzzz
 */
@Getter
public final class PrometheusRouteRequest {

    private final PrometheusRouteHttpMethod method;
    private final String relativePath;
    private final List<PrometheusRouteParameter> queryParameters;
    private final List<PrometheusRouteHeader> headers;
    private final byte[] body;

    /**
     * 创建受控 HTTP 请求。
     *
     * @param method          HTTP 方法，仅支持 GET 或 POST
     * @param relativePath    固定 target 下的相对路径
     * @param queryParameters 有序 query 参数
     * @param headers         请求 header
     * @param body            POST 请求正文
     */
    public PrometheusRouteRequest(PrometheusRouteHttpMethod method, String relativePath,
                                  List<PrometheusRouteParameter> queryParameters,
                                  List<PrometheusRouteHeader> headers, byte[] body) {
        if (method == null || relativePath == null || relativePath.trim().isEmpty()
                || method == PrometheusRouteHttpMethod.GET && body != null && body.length > 0) {
            throw new IllegalArgumentException(ErrorMessage.REQUEST_ILLEGAL);
        }
        this.method = method;
        this.relativePath = relativePath;
        this.queryParameters = immutableCopy(queryParameters);
        this.headers = immutableCopy(headers);
        this.body = body == null ? null : body.clone();
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException(ErrorMessage.REQUEST_ILLEGAL);
            }
        }
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }


    public byte[] getBody() {
        return body == null ? null : body.clone();
    }
}
