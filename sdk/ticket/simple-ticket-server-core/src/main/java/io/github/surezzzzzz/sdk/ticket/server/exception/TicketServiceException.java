package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;

/**
 * 工单内部依赖或数据处理失败，不向调用方暴露底层细节。
 *
 * @author surezzzzzz
 */
public class TicketServiceException extends SimpleTicketServerException {

    /**
     * 以内部安全消息构造。
     *
     * @param message 内部诊断消息
     */
    public TicketServiceException(String message) {
        super(ErrorCode.TICKET_SERVICE_ERROR, message);
    }

    public TicketServiceException(Throwable cause) {
        super(ErrorCode.TICKET_SERVICE_ERROR, ErrorMessage.TICKET_SERVICE_ERROR, cause);
    }
}
