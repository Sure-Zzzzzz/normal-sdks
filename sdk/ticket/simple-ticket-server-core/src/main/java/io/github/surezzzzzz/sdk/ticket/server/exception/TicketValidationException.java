package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import lombok.Getter;

/**
 * 字段校验失败（400，字段级消息）。
 *
 * @author surezzzzzz
 */
@Getter
public class TicketValidationException extends SimpleTicketServerException {

    /**
     * 字段名
     */
    private final String field;

    /**
     * 以字段与原因构造。
     *
     * @param field  字段名
     * @param reason 原因
     */
    public TicketValidationException(String field, String reason) {
        super(ErrorCode.TICKET_VALIDATION_FAILED, field + " " + reason);
        this.field = field;
    }

}
