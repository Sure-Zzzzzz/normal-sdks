package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;

/**
 * 类型、处理组等资源的唯一键冲突。
 *
 * @author surezzzzzz
 */
public class TicketResourceConflictException extends SimpleTicketServerException {

    /** 以默认安全消息构造。 */
    public TicketResourceConflictException() {
        super(ErrorCode.TICKET_RESOURCE_CONFLICT, ErrorMessage.TICKET_RESOURCE_CONFLICT);
    }
}
