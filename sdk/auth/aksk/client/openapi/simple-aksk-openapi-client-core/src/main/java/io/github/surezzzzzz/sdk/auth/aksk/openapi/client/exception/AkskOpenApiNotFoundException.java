package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 资源不存在（404）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiNotFoundException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiNotFoundException(String message, String method, String endpoint, String requestId) {
        super(message, 404, method, endpoint, requestId, null);
    }

    public AkskOpenApiNotFoundException(String message, String method, String endpoint, String requestId,
                                        Throwable cause) {
        super(message, 404, method, endpoint, requestId, cause);
    }
}
