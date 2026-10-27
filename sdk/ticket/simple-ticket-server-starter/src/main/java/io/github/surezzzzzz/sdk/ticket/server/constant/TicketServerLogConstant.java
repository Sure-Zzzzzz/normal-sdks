package io.github.surezzzzzz.sdk.ticket.server.constant;

/**
 * 工单服务日志边界常量。
 *
 * @author surezzzzzz
 */
public final class TicketServerLogConstant {

    /**
     * 内部错误日志最多保留的栈帧数。
     */
    public static final int MAX_ERROR_STACK_FRAMES = 10;

    private TicketServerLogConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
