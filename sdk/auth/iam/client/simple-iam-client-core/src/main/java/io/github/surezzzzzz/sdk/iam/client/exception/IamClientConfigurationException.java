package io.github.surezzzzzz.sdk.iam.client.exception;

import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;

/**
 * 客户端配置不合法（如 base-url 带 path）。
 *
 * @author surezzzzzz
 */
public class IamClientConfigurationException extends SimpleIamClientException {

    private static final long serialVersionUID = 1L;

    public IamClientConfigurationException() {
        super("IAM_CONFIG_001", SimpleIamClientConstant.MESSAGE_INVALID_CONFIGURATION);
    }
}
