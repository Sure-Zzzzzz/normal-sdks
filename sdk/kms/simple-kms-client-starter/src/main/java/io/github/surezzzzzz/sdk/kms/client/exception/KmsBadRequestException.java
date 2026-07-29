package io.github.surezzzzzz.sdk.kms.client.exception;

import java.time.Instant;

/**
 * KMS 拒绝请求参数、方法或媒体类型时的稳定异常。
 */
public class KmsBadRequestException extends SimpleKmsClientException {
    /**
     * 创建请求错误。
     *
     * @param message   安全错误消息
     * @param status    HTTP 状态码
     * @param method    HTTP 方法
     * @param endpoint  非敏感接口路径
     * @param requestId 服务端请求标识
     * @param timestamp 服务端错误时间
     */
    public KmsBadRequestException(String message, Integer status, String method, String endpoint, String requestId, Instant timestamp) {
        super(message, status, method, endpoint, requestId, timestamp);
    }
}
