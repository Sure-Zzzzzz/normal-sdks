package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception;

/**
 * Simple AKSK Resource Server 配置异常
 *
 * <p>当 resource server 安全配置不合法时抛出此异常。
 *
 * @author surezzzzzz
 */
public class SimpleAkskResourceServerConfigurationException extends RuntimeException {

    public SimpleAkskResourceServerConfigurationException(String message) {
        super(message);
    }

    public SimpleAkskResourceServerConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
