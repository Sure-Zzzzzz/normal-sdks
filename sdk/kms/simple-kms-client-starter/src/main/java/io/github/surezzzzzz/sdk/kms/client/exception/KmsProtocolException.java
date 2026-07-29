package io.github.surezzzzzz.sdk.kms.client.exception;

/**
 * Client 收到不符合既定 HTTP 或 JSON 契约内容时的稳定异常。
 */
public class KmsProtocolException extends SimpleKmsClientException {

    /**
     * 创建协议错误。
     *
     * @param message 安全错误消息
     */
    public KmsProtocolException(String message) {
        super(message);
    }

    /**
     * 创建携带底层原因的协议错误。
     *
     * @param message 安全错误消息
     * @param cause   原始异常
     */
    public KmsProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
