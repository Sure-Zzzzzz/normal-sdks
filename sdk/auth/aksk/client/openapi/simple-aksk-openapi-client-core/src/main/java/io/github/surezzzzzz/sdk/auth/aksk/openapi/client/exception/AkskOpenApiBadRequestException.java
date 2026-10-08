package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 请求参数不合法（400）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiBadRequestException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiBadRequestException(String message, String method, String endpoint, String requestId) {
        super(message, 400, method, endpoint, requestId, null);
    }

    public AkskOpenApiBadRequestException(String message, String method, String endpoint, String requestId,
                                          Throwable cause) {
        super(message, 400, method, endpoint, requestId, cause);
    }
}
