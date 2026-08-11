package io.github.surezzzzzz.sdk.ops.middleware.exception;

import org.springframework.http.HttpStatus;

/**
 * 可安全映射到 HTTP 的运维异常。
 *
 * @author surezzzzzz
 */
public class MiddlewareOpsException extends RuntimeException {

    private final HttpStatus status;

    /**
     * 创建安全异常。
     *
     * @param status  HTTP 状态
     * @param message 安全中文消息
     */
    public MiddlewareOpsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * 创建带根因的安全异常。
     *
     * @param status  HTTP 状态
     * @param message 安全中文消息
     * @param cause   原始异常
     */
    public MiddlewareOpsException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * 获取 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus getStatus() {
        return status;
    }
}
