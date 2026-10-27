package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 工单类型不存在。
 *
 * @author surezzzzzz
 */
public class TicketTypeNotFoundException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketTypeNotFoundException() {
        super(ErrorCode.TICKET_TYPE_NOT_FOUND, ErrorMessage.TICKET_TYPE_NOT_FOUND);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketTypeNotFoundException(String message) {
        super(ErrorCode.TICKET_TYPE_NOT_FOUND, message);
    }

}
