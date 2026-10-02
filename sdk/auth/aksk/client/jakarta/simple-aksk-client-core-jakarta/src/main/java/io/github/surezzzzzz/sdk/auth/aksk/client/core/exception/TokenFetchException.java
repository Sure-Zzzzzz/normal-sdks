package io.github.surezzzzzz.sdk.auth.aksk.client.core.exception;

/**
 * Token Fetch Exception
 * <p>
 * 当从 OAuth2 Server 获取 Token 失败时抛出
 * <p>
 * 适用场景：
 * <ul>
 *   <li>HTTP 请求失败</li>
 *   <li>HTTP 响应无效</li>
 *   <li>网络超时</li>
 *   <li>认证失败</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public class TokenFetchException extends SimpleAkskClientCoreException {

    private static final long serialVersionUID = 1L;

    public TokenFetchException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TokenFetchException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
