package io.github.surezzzzzz.sdk.mysql.route.exception;

import lombok.Getter;

/**
 * MySQL Route 运行期异常。
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleMysqlRouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public SimpleMysqlRouteException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SimpleMysqlRouteException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
