package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 响应不符合契约形态（无法反序列化为 wire DTO）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiProtocolException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiProtocolException(String message, String method, String endpoint, String requestId) {
        super(message, null, method, endpoint, requestId, null);
    }

    public AkskOpenApiProtocolException(String message, String method, String endpoint, String requestId,
                                        Throwable cause) {
        super(message, null, method, endpoint, requestId, cause);
    }
}
