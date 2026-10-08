package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 客户端装配缺件（如传输层或序列化器缺失）。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiClientConfigurationException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiClientConfigurationException(String message, String method, String endpoint, String requestId) {
        super(message, null, method, endpoint, requestId, null);
    }

    public AkskOpenApiClientConfigurationException(String message, String method, String endpoint, String requestId,
                                                   Throwable cause) {
        super(message, null, method, endpoint, requestId, cause);
    }
}
