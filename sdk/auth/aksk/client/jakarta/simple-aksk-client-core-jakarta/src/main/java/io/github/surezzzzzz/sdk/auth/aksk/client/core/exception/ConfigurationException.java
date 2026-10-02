package io.github.surezzzzzz.sdk.auth.aksk.client.core.exception;

/**
 * Configuration Exception
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleAkskClientCoreException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
