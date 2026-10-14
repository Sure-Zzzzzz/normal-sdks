package io.github.surezzzzzz.sdk.crm.client.exception;

/**
 * CRM 客户端基础异常。
 *
 * @author surezzzzzz
 */
public class CrmClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建异常。
     *
     * @param message 消息
     */
    public CrmClientException(String message) {
        super(message);
    }

    /**
     * 创建异常。
     *
     * @param message 消息
     * @param cause   根因
     */
    public CrmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
