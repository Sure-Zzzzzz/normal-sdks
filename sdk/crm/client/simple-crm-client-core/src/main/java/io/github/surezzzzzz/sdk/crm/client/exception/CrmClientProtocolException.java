package io.github.surezzzzzz.sdk.crm.client.exception;

/**
 * 不可重试协议异常：4xx 语义错误或响应校验失败（未知字段、必填缺失或字段类型非法）。
 *
 * @author surezzzzzz
 */
public class CrmClientProtocolException extends CrmClientException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * 创建协议异常。
     *
     * @param statusCode HTTP 状态码
     * @param message    消息
     */
    public CrmClientProtocolException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * HTTP 状态码。
     *
     * @return 状态码
     */
    public int getStatusCode() {
        return statusCode;
    }
}
