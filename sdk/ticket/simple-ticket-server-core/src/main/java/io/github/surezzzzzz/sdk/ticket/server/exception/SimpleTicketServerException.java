package io.github.surezzzzzz.sdk.ticket.server.exception;

import lombok.Getter;

/**
 * 工单领域异常基类（携带错误码，HTTP 语义映射见 DESIGN §7.4）。
 *
 * @author surezzzzzz
 */
@Getter
public abstract class SimpleTicketServerException extends RuntimeException {

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 以错误码与消息构造。
     *
     * @param errorCode 错误码
     * @param message   消息
     */
    protected SimpleTicketServerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 以错误码、消息与原因构造。
     *
     * @param errorCode 错误码
     * @param message   消息
     * @param cause     原因
     */
    protected SimpleTicketServerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
