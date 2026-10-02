package io.github.surezzzzzz.sdk.auth.aksk.client.core.exception;

/**
 * Token Lock Exception
 * <p>
 * 当分布式锁操作失败时抛出
 * <p>
 * 适用场景：
 * <ul>
 *   <li>分布式锁获取失败</li>
 *   <li>分布式锁等待中断</li>
 *   <li>锁超时</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public class TokenLockException extends SimpleAkskClientCoreException {

    private static final long serialVersionUID = 1L;

    public TokenLockException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TokenLockException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
