package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.*;

/**
 * HTTP 状态码到异常族的映射（RestTemplate 形态使用；Feign 形态维持裸 FeignException，不经过本类）。
 *
 * <p>消息只携带非敏感诊断元数据，不含响应体原文。</p>
 *
 * @author surezzzzzz
 */
public final class AkskOpenApiHttpErrorMapper {

    private AkskOpenApiHttpErrorMapper() {
    }

    /**
     * 将 HTTP 错误状态映射为异常族实例。
     *
     * @param status    HTTP 状态码
     * @param method    HTTP 方法（诊断用）
     * @param endpoint  目标端点路径（不含查询串）
     * @param requestId 请求标识（可为 null）
     * @return 对应语义的异常
     */
    public static RuntimeException map(int status, String method, String endpoint, String requestId) {
        String message = "AKSK OpenAPI 调用失败：status=" + status + ", method=" + method + ", endpoint=" + endpoint;
        switch (status) {
            case 400:
                return new AkskOpenApiBadRequestException(message, method, endpoint, requestId);
            case 401:
                return new AkskOpenApiUnauthenticatedException(message, method, endpoint, requestId);
            case 403:
                return new AkskOpenApiUnauthorizedException(message, method, endpoint, requestId);
            case 404:
                return new AkskOpenApiNotFoundException(message, method, endpoint, requestId);
            case 409:
                return new AkskOpenApiConflictException(message, method, endpoint, requestId);
            case 413:
                return new AkskOpenApiPayloadTooLargeException(message, method, endpoint, requestId);
            case 422:
                return new AkskOpenApiUnprocessableException(message, method, endpoint, requestId);
            case 503:
                return new AkskOpenApiServiceUnavailableException(message, method, endpoint, requestId);
            default:
                return new io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.SimpleAkskOpenApiClientException(
                        message, status, method, endpoint, requestId, null);
        }
    }
}
