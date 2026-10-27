package io.github.surezzzzzz.sdk.ticket.server.exception;

import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;

import java.util.Arrays;

/**
 * 状态冲突（非法转移，409）。
 *
 * @author surezzzzzz
 */
public class TicketStateException extends SimpleTicketServerException {

    /**
     * 以动作与状态构造（消息含期望状态，不含敏感数据）。
     *
     * @param action  动作名
     * @param current 当前状态
     * @param expected 期望状态集合
     */
    public TicketStateException(String action, TicketStatus current, TicketStatus... expected) {
        super(ErrorCode.TICKET_STATE_CONFLICT,
                ErrorMessage.TICKET_STATE_CONFLICT + "：动作=" + action + "，当前=" + current
                        + "，期望=" + Arrays.toString(expected));
    }
}
