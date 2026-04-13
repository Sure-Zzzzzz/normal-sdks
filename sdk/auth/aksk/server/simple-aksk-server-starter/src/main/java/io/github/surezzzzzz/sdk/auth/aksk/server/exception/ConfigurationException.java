package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;

/**
 * Configuration Exception
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleAkskServerException {

    public ConfigurationException(String message) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message, cause);
    }
}
