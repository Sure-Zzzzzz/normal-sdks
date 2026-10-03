package io.github.surezzzzzz.sdk.kms.client.exception;

import lombok.Getter;

import java.time.Instant;

/**
 * KMS Client 稳定异常基类。
 *
 * <p>仅保留 HTTP 状态、方法、非敏感路径、请求标识和时间等诊断元数据，绝不保存原始请求或响应载荷。</p>
 *
 * @author surezzzzzz
 */
@Getter
/**
 * KMS Client 异常族根异常。

 <p>子类按 HTTP 语义划分；可安全携带 status、method、endpoint、requestId 与 timestamp，
 不得携带原始响应体、认证头、明文、密文、签名、AAD 或 envelope。</p>
 *
 * @author surezzzzzz
 */
public class SimpleKmsClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer status;
    private final String method;
    private final String endpoint;
    private final String requestId;
    private final Instant timestamp;

    /**
     * 创建 Client 异常。
     *
     * @param message 安全错误消息
     */
    public SimpleKmsClientException(String message) {
        this(message, null, null, null, null, null, null);
    }

    /**
     * 创建 Client 异常。
     *
     * @param message 安全错误消息
     * @param cause   原始异常
     */
    public SimpleKmsClientException(String message, Throwable cause) {
        this(message, null, null, null, null, null, cause);
    }

    /**
     * 创建携带安全响应元数据的 Client 异常。
     */
    public SimpleKmsClientException(String message, Integer status, String method, String endpoint,
                                    String requestId, Instant timestamp) {
        this(message, status, method, endpoint, requestId, timestamp, null);
    }

    private SimpleKmsClientException(String message, Integer status, String method, String endpoint,
                                     String requestId, Instant timestamp, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.method = method;
        this.endpoint = endpoint;
        this.requestId = requestId;
        this.timestamp = timestamp;
    }
}
