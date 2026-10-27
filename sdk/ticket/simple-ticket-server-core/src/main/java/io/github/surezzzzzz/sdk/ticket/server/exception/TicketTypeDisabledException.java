package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 工单类型已停用。
 *
 * @author surezzzzzz
 */
public class TicketTypeDisabledException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketTypeDisabledException() {
        super(ErrorCode.TICKET_TYPE_DISABLED, ErrorMessage.TICKET_TYPE_DISABLED);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketTypeDisabledException(String message) {
        super(ErrorCode.TICKET_TYPE_DISABLED, message);
    }

}
