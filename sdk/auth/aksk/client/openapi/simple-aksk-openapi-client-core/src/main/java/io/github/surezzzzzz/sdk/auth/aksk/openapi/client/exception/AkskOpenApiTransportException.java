package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 传输层异常（连接失败/中断），无 HTTP 状态。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiTransportException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiTransportException(String message, String method, String endpoint, String requestId) {
        super(message, null, method, endpoint, requestId, null);
    }

    public AkskOpenApiTransportException(String message, String method, String endpoint, String requestId,
                                         Throwable cause) {
        super(message, null, method, endpoint, requestId, cause);
    }
}
