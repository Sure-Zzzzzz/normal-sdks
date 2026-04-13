package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

/**
 * Client Exception
 *
 * @author surezzzzzz
 */
public class ClientException extends SimpleAkskServerException {

    public ClientException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ClientException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
