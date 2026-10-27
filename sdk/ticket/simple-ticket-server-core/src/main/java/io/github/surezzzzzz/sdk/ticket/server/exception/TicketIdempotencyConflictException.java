package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 幂等键同键异体（409）。
 *
 * @author surezzzzzz
 */
public class TicketIdempotencyConflictException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketIdempotencyConflictException() {
        super(ErrorCode.TICKET_IDEMPOTENT_CONFLICT, ErrorMessage.TICKET_IDEMPOTENT_CONFLICT);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketIdempotencyConflictException(String message) {
        super(ErrorCode.TICKET_IDEMPOTENT_CONFLICT, message);
    }

}
