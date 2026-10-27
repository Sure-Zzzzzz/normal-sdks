package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 处理组存在在途单。
 *
 * @author surezzzzzz
 */
public class TicketGroupHasOpenTicketsException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketGroupHasOpenTicketsException() {
        super(ErrorCode.TICKET_GROUP_HAS_OPEN_TICKETS, ErrorMessage.TICKET_GROUP_HAS_OPEN_TICKETS);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketGroupHasOpenTicketsException(String message) {
        super(ErrorCode.TICKET_GROUP_HAS_OPEN_TICKETS, message);
    }

}
