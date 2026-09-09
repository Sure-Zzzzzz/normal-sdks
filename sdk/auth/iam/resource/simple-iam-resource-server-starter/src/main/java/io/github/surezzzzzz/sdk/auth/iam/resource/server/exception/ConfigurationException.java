package io.github.surezzzzzz.sdk.auth.iam.resource.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.ErrorCode;

/**
 * Configuration Exception
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleIamResourceServerException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
    }
}
