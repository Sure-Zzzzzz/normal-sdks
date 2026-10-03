package io.github.surezzzzzz.sdk.kms.client.exception;

/**
 * Client 发现响应超过本地安全读取上限时的稳定异常。
 */
public class KmsResponseTooLargeException extends SimpleKmsClientException {

    /**
     * 创建响应过大错误。
     *
     * @param message 安全错误消息
     */
    public KmsResponseTooLargeException(String message) {
        super(message);
    }
}
