package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception;

/**
 * 状态冲突（409）：并发修改或生命周期约束。
 *
 * @author surezzzzzz
 */
public class AkskOpenApiConflictException extends SimpleAkskOpenApiClientException {

    private static final long serialVersionUID = 1L;

    public AkskOpenApiConflictException(String message, String method, String endpoint, String requestId) {
        super(message, 409, method, endpoint, requestId, null);
    }

    public AkskOpenApiConflictException(String message, String method, String endpoint, String requestId,
                                        Throwable cause) {
        super(message, 409, method, endpoint, requestId, cause);
    }
}
