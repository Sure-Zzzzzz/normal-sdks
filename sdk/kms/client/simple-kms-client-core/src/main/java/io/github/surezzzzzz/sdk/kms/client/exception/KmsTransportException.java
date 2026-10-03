package io.github.surezzzzzz.sdk.kms.client.exception;

/**
 * HTTP 连接、读写等传输层失败时的稳定异常，不代表请求未被服务端处理。
 */
public class KmsTransportException extends SimpleKmsClientException {

    /**
     * 创建通信错误。
     *
     * @param message 安全错误消息
     * @param cause   原始异常
     */
    public KmsTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
