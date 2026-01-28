package io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception;

import lombok.Getter;

/**
 * Simple AKSK 表达式异常
 *
 * <p>当 SpEL 表达式解析或执行失败时抛出此异常。
 *
 * <p>与 {@link SimpleAkskSecurityException} 的区别：
 * <ul>
 *   <li>{@link SimpleAkskSecurityException} - 权限校验失败（业务逻辑错误）</li>
 *   <li>{@link SimpleAkskExpressionException} - 表达式解析/执行失败（技术错误）</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>SpEL 表达式语法错误</li>
 *   <li>表达式执行时抛出异常（如 NullPointerException、访问不存在的方法等）</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Getter
public class SimpleAkskExpressionException extends SimpleAkskSecurityException {

    /**
     * 原始表达式
     * -- GETTER --
     * 获取导致异常的表达式
     *
     * @return 表达式字符串
     */
    private final String expression;

    public SimpleAkskExpressionException(String message, String expression) {
        super(message);
        this.expression = expression;
    }

    public SimpleAkskExpressionException(String message, String expression, Throwable cause) {
        super(message, cause);
        this.expression = expression;
    }

}
