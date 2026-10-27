package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 工单不可见（404 掩蔽越权）。
 *
 * @author surezzzzzz
 */
public class TicketNotAccessibleException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketNotAccessibleException() {
        super(ErrorCode.TICKET_NOT_ACCESSIBLE, ErrorMessage.TICKET_NOT_ACCESSIBLE);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketNotAccessibleException(String message) {
        super(ErrorCode.TICKET_NOT_ACCESSIBLE, message);
    }

}
