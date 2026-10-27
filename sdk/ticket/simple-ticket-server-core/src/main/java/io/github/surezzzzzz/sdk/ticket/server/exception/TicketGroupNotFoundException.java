package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 处理组不存在。
 *
 * @author surezzzzzz
 */
public class TicketGroupNotFoundException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketGroupNotFoundException() {
        super(ErrorCode.TICKET_GROUP_NOT_FOUND, ErrorMessage.TICKET_GROUP_NOT_FOUND);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketGroupNotFoundException(String message) {
        super(ErrorCode.TICKET_GROUP_NOT_FOUND, message);
    }

}
