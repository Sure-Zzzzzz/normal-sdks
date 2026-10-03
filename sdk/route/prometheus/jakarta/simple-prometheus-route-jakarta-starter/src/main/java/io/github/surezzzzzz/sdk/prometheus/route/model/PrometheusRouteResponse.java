package io.github.surezzzzzz.sdk.prometheus.route.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Route 不可变响应快照。
 *
 * @author surezzzzzz
 */
@Getter
public final class PrometheusRouteResponse {

    private final int statusCode;
    private final List<PrometheusRouteHeader> headers;
    private final byte[] body;

    private PrometheusRouteResponse(int statusCode, List<PrometheusRouteHeader> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = Collections.unmodifiableList(new ArrayList<PrometheusRouteHeader>(headers));
        this.body = body == null ? new byte[0] : body.clone();
    }

    /**
     * 创建与底层连接资源脱离的响应快照。
     *
     * @param statusCode HTTP 状态码
     * @param headers    响应 header
     * @param body       响应正文
     * @return 不可变响应快照
     */
    public static PrometheusRouteResponse of(int statusCode, List<PrometheusRouteHeader> headers, byte[] body) {
        return new PrometheusRouteResponse(statusCode,
                headers == null ? Collections.<PrometheusRouteHeader>emptyList() : headers, body);
    }


    public byte[] getBody() {
        return body.clone();
    }
}
