package io.github.surezzzzzz.sdk.kms.client.support;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.*;

import java.time.Instant;

/**
 * KMS HTTP 错误映射器。
 *
 * <p>将服务端状态稳定映射为可处理的 Client 异常类型；仅接收已提取的安全错误字段，
 * 不接收或保存原始响应体。</p>
 *
 * @author surezzzzzz
 */
public class KmsHttpErrorMapper {

    /**
     * 按 HTTP 状态构造安全的 Client 异常。
     *
     * @param status    HTTP 状态码
     * @param method    HTTP 方法
     * @param endpoint  非敏感接口路径
     * @param message   服务端安全错误消息
     * @param requestId 服务端请求标识
     * @param timestamp 服务端错误时间
     * @return 对应的 Client 异常
     */
    public SimpleKmsClientException map(int status, String method, String endpoint, String message,
                                        String requestId, Instant timestamp) {
        if (status == SimpleKmsClientConstant.HTTP_STATUS_BAD_REQUEST
                || status == SimpleKmsClientConstant.HTTP_STATUS_METHOD_NOT_ALLOWED
                || status == SimpleKmsClientConstant.HTTP_STATUS_UNSUPPORTED_MEDIA_TYPE) {
            return new KmsBadRequestException(message, status, method, endpoint, requestId, timestamp);
        }
        if (status == SimpleKmsClientConstant.HTTP_STATUS_UNAUTHORIZED) {
            return new KmsUnauthenticatedException(message, status, method, endpoint, requestId, timestamp);
        }
        if (status == SimpleKmsClientConstant.HTTP_STATUS_FORBIDDEN) {
            return new KmsUnauthorizedException(message, status, method, endpoint, requestId, timestamp);
        }
        if (status == SimpleKmsClientConstant.HTTP_STATUS_NOT_FOUND) {
            return new KmsNotFoundException(message, status, method, endpoint, requestId, timestamp);
        }
        if (status == SimpleKmsClientConstant.HTTP_STATUS_CONFLICT) {
            return new KmsConflictException(message, status, method, endpoint, requestId, timestamp);
        }
        if (status == SimpleKmsClientConstant.HTTP_STATUS_PAYLOAD_TOO_LARGE) {
            return new KmsPayloadTooLargeException(message, status, method, endpoint, requestId, timestamp);
        }
        if (status == SimpleKmsClientConstant.HTTP_STATUS_UNPROCESSABLE_ENTITY) {
            return new KmsUnprocessableException(message, status, method, endpoint, requestId, timestamp);
        }
        return new KmsServiceUnavailableException(message, status, method, endpoint, requestId, timestamp);
    }
}
