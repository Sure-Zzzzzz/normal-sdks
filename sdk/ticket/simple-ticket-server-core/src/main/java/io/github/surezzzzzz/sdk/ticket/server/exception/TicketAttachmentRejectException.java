package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 附件被拒。
 *
 * @author surezzzzzz
 */
public class TicketAttachmentRejectException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketAttachmentRejectException() {
        super(ErrorCode.TICKET_ATTACHMENT_REJECTED, ErrorMessage.TICKET_ATTACHMENT_REJECTED);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketAttachmentRejectException(String message) {
        super(ErrorCode.TICKET_ATTACHMENT_REJECTED, message);
    }

}
