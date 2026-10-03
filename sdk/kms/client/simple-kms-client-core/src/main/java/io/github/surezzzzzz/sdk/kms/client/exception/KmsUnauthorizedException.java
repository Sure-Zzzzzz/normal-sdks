package io.github.surezzzzzz.sdk.kms.client.exception;

import java.time.Instant;

/**
 * KMS 已识别调用方但拒绝其操作授权时的稳定异常。
 */
public class KmsUnauthorizedException extends SimpleKmsClientException {
    /**
     * 创建未授权错误。
     *
     * @param message   安全错误消息
     * @param status    HTTP 状态码
     * @param method    HTTP 方法
     * @param endpoint  非敏感接口路径
     * @param requestId 服务端请求标识
     * @param timestamp 服务端错误时间
     */
    public KmsUnauthorizedException(String message, Integer status, String method, String endpoint, String requestId, Instant timestamp) {
        super(message, status, method, endpoint, requestId, timestamp);
    }
}
