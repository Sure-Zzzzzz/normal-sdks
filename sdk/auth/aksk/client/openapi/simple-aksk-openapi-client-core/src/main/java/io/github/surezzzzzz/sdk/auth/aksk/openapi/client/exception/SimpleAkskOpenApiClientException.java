package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

import lombok.Getter;

import java.time.Instant;

/**
 * AKSK OpenAPI 客户端稳定异常基类。
 *
 * <p>仅保留 HTTP 状态、方法、非敏感路径、请求标识和时间等诊断元数据；绝不保存原始请求或响应载荷、
 * Authorization 头、clientSecret 或 Token。子类按 HTTP 语义划分。</p>
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleAkskOpenApiClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP 状态码（传输层异常时为 null）。
     */
    private final Integer status;

    /**
     * HTTP 方法（诊断用，非敏感）。
     */
    private final String method;

    /**
     * 目标端点路径（不含查询串，非敏感）。
     */
    private final String endpoint;

    /**
     * 请求标识（如响应头 request-id，可为 null）。
     */
    private final String requestId;

    /**
     * 异常时间。
     */
    private final Instant timestamp;

    public SimpleAkskOpenApiClientException(String message, Integer status, String method,
                                            String endpoint, String requestId, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.method = method;
        this.endpoint = endpoint;
        this.requestId = requestId;
        this.timestamp = Instant.now();
    }
}
