package io.github.surezzzzzz.sdk.kms.client.exception;

import java.time.Instant;

/**
 * KMS 请求的逻辑资源或版本不存在时的稳定异常。
 */
public class KmsNotFoundException extends SimpleKmsClientException {
    /**
     * 创建资源不存在错误。
     *
     * @param message   安全错误消息
     * @param status    HTTP 状态码
     * @param method    HTTP 方法
     * @param endpoint  非敏感接口路径
     * @param requestId 服务端请求标识
     * @param timestamp 服务端错误时间
     */
    public KmsNotFoundException(String message, Integer status, String method, String endpoint, String requestId, Instant timestamp) {
        super(message, status, method, endpoint, requestId, timestamp);
    }
}
