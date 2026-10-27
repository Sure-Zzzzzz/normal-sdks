package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 类型模板不合法（管理面）。
 *
 * @author surezzzzzz
 */
public class TicketTemplateInvalidException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketTemplateInvalidException() {
        super(ErrorCode.TICKET_TEMPLATE_INVALID, ErrorMessage.TICKET_TEMPLATE_INVALID);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketTemplateInvalidException(String message) {
        super(ErrorCode.TICKET_TEMPLATE_INVALID, message);
    }

}
