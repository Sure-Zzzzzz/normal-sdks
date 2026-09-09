package io.github.surezzzzzz.sdk.auth.iam.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;

/**
 * Configuration Exception
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleIamServerException {

    public ConfigurationException(String message) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message, cause);
    }

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
