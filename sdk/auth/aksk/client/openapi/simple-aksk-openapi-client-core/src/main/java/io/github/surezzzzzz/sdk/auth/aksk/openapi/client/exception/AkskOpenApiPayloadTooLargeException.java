package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 请求体超限（413）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiPayloadTooLargeException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiPayloadTooLargeException(String message, String method, String endpoint, String requestId) {
        super(message, 413, method, endpoint, requestId, null);
    }

    public AkskOpenApiPayloadTooLargeException(String message, String method, String endpoint, String requestId,
                                               Throwable cause) {
        super(message, 413, method, endpoint, requestId, cause);
    }
}
