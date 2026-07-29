package io.github.surezzzzzz.sdk.kms.client.exception;

/**
 * KMS Client 启动配置或专属扩展点组合不合法时的稳定异常。
 *
 * @author surezzzzzz
 */
public class KmsClientConfigurationException extends SimpleKmsClientException {

    /**
     * 创建配置错误。
     *
     * @param message 安全错误消息
     */
    public KmsClientConfigurationException(String message) {
        super(message);
    }

    /**
     * 创建携带底层原因的配置错误。
     *
     * @param message 安全错误消息
     * @param cause   原始异常
     */
    public KmsClientConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
