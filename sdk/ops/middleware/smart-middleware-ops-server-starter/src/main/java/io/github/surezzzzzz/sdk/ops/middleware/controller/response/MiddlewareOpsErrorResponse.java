package io.github.surezzzzzz.sdk.ops.middleware.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 安全运维错误响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsErrorResponse {

    /**
     * 安全中文消息。
     */
    private final String message;
    /**
     * UTC 时间戳。
     */
    private final Instant timestamp;
    /**
     * 请求标识。
     */
    private final String requestId;
}
