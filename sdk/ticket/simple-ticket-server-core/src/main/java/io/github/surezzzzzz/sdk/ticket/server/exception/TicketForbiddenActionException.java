package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 可见但角色不符（403）。
 *
 * @author surezzzzzz
 */
public class TicketForbiddenActionException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketForbiddenActionException() {
        super(ErrorCode.TICKET_FORBIDDEN_ACTION, ErrorMessage.TICKET_FORBIDDEN_ACTION);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketForbiddenActionException(String message) {
        super(ErrorCode.TICKET_FORBIDDEN_ACTION, message);
    }

}
