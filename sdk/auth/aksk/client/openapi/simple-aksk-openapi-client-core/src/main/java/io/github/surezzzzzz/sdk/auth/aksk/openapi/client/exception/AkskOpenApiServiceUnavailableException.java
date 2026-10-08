package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 服务不可用（503）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiServiceUnavailableException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiServiceUnavailableException(String message, String method, String endpoint, String requestId) {
        super(message, 503, method, endpoint, requestId, null);
    }

    public AkskOpenApiServiceUnavailableException(String message, String method, String endpoint, String requestId,
                                                  Throwable cause) {
        super(message, 503, method, endpoint, requestId, cause);
    }
}
