package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 授权不足（403）：缺少精确 API permission 或 DATA 越界。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiUnauthorizedException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiUnauthorizedException(String message, String method, String endpoint, String requestId) {
        super(message, 403, method, endpoint, requestId, null);
    }

    public AkskOpenApiUnauthorizedException(String message, String method, String endpoint, String requestId,
                                            Throwable cause) {
        super(message, 403, method, endpoint, requestId, cause);
    }
}
