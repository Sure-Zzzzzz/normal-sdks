package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
/**
 * 附件不存在。
 *
 * @author surezzzzzz
 */
public class TicketAttachmentNotFoundException extends SimpleTicketServerException {

    /**
     * 以默认消息构造。
     */
    public TicketAttachmentNotFoundException() {
        super(ErrorCode.TICKET_ATTACHMENT_NOT_FOUND, ErrorMessage.TICKET_ATTACHMENT_NOT_FOUND);
    }

    /**
     * 以自定义消息构造。
     *
     * @param message 消息
     */
    public TicketAttachmentNotFoundException(String message) {
        super(ErrorCode.TICKET_ATTACHMENT_NOT_FOUND, message);
    }

}
