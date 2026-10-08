package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 认证失败（401）：Token 缺失/过期/撤销，或管理凭据无效。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiUnauthenticatedException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiUnauthenticatedException(String message, String method, String endpoint, String requestId) {
        super(message, 401, method, endpoint, requestId, null);
    }

    public AkskOpenApiUnauthenticatedException(String message, String method, String endpoint, String requestId,
                                               Throwable cause) {
        super(message, 401, method, endpoint, requestId, cause);
    }
}
