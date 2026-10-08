package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 语义无法处理（422）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiUnprocessableException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiUnprocessableException(String message, String method, String endpoint, String requestId) {
        super(message, 422, method, endpoint, requestId, null);
    }

    public AkskOpenApiUnprocessableException(String message, String method, String endpoint, String requestId,
                                             Throwable cause) {
        super(message, 422, method, endpoint, requestId, cause);
    }
}
