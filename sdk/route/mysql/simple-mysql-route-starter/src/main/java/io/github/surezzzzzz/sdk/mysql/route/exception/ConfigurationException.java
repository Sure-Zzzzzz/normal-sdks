package io.github.surezzzzzz.sdk.mysql.route.exception;

/**
 * MySQL Route 配置异常。
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleMysqlRouteException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String code, String message) {
        super(code, message);
    }

    public ConfigurationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
