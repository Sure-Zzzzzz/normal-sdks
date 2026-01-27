package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.exception;

/**
 * AKSK 安全异常
 *
 * <p>当安全校验失败时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>@RequireContext - 安全上下文不存在</li>
 *   <li>@RequireField - 必需字段不存在</li>
 *   <li>@RequireFieldValue - 字段值不匹配</li>
 *   <li>@RequireExpression - SpEL 表达式计算结果为 false</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class AkskSecurityException extends RuntimeException {

    public AkskSecurityException(String message) {
        super(message);
    }

    public AkskSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
