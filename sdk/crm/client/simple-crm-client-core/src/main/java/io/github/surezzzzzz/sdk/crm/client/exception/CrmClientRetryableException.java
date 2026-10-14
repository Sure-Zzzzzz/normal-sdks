package io.github.surezzzzzz.sdk.crm.client.exception;

/**
 * 可重试异常：网络/超时/5xx——写命令重发安全由服务端幂等键语义保证。
 *
 * @author surezzzzzz
 */
public class CrmClientRetryableException extends CrmClientException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建可重试异常。
     *
     * @param message 消息
     * @param cause   根因
     */
    public CrmClientRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
