package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 响应体超过客户端允许范围。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiResponseTooLargeException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiResponseTooLargeException(String message, String method, String endpoint, String requestId) {
        super(message, null, method, endpoint, requestId, null);
    }

    public AkskOpenApiResponseTooLargeException(String message, String method, String endpoint, String requestId,
                                                Throwable cause) {
        super(message, null, method, endpoint, requestId, cause);
    }
}
