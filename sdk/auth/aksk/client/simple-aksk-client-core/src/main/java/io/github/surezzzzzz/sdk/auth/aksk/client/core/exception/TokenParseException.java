package io.github.surezzzzzz.sdk.auth.aksk.client.core.exception;

/**
 * Token Parse Exception
 * <p>
 * 当 Token 解析失败时抛出
 * <p>
 * 适用场景：
 * <ul>
 *   <li>JWT 格式无效</li>
 *   <li>Token 签名验证失败</li>
 *   <li>Token 已过期</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public class TokenParseException extends SimpleAkskClientCoreException {

    private static final long serialVersionUID = 1L;

    public TokenParseException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TokenParseException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
