package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import lombok.Value;

/**
 * HTTP 错误的安全说明，不携带业务错误码或异常类型。
 */
@Value
public class SearchErrorResponse {
    /**
     * 面向调用方的固定安全消息、ISO 时间及请求关联标识。
     */
    String message, timestamp, requestId;
}
