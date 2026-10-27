package io.github.surezzzzzz.sdk.ticket.server.statemachine;

import io.github.surezzzzzz.sdk.ticket.server.constant.TicketAction;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机转移表（纯函数，DESIGN §6.4 全枚举）。
 *
 * <p>领域执行在 {@code Ticket} 的富方法内自校验；本表提供动作×状态的合法前置集合与目标状态，
 * 供测试双向对齐断言与队列过滤/文档口径使用。COMMENT 不改状态，不在此表。</p>
 *
 * @author surezzzzzz
 */
public final class TicketStateMachine {

    private TicketStateMachine() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final Map<TicketAction, Set<TicketStatus>> ALLOWED_FROM = new EnumMap<>(TicketAction.class);

    private static final Map<TicketAction, TicketStatus> TARGET = new EnumMap<>(TicketAction.class);

    static {
        ALLOWED_FROM.put(TicketAction.CLAIM, EnumSet.of(TicketStatus.SUBMITTED));
        ALLOWED_FROM.put(TicketAction.ASSIGN, EnumSet.of(TicketStatus.SUBMITTED, TicketStatus.PROCESSING,
                TicketStatus.PENDING_INFO));
        ALLOWED_FROM.put(TicketAction.TRANSFER, EnumSet.of(TicketStatus.PROCESSING, TicketStatus.PENDING_INFO));
        ALLOWED_FROM.put(TicketAction.REQUEST_INFO, EnumSet.of(TicketStatus.PROCESSING));
        ALLOWED_FROM.put(TicketAction.SUPPLY, EnumSet.of(TicketStatus.PENDING_INFO));
        ALLOWED_FROM.put(TicketAction.RESOLVE, EnumSet.of(TicketStatus.PROCESSING, TicketStatus.PENDING_INFO));
        ALLOWED_FROM.put(TicketAction.CONFIRM, EnumSet.of(TicketStatus.RESOLVED));
        ALLOWED_FROM.put(TicketAction.REJECT, EnumSet.of(TicketStatus.RESOLVED));
        ALLOWED_FROM.put(TicketAction.WITHDRAW, EnumSet.of(TicketStatus.SUBMITTED, TicketStatus.PROCESSING,
                TicketStatus.PENDING_INFO));
        ALLOWED_FROM.put(TicketAction.CONFIRM_TIMEOUT, EnumSet.of(TicketStatus.RESOLVED));

        TARGET.put(TicketAction.CLAIM, TicketStatus.PROCESSING);
        TARGET.put(TicketAction.ASSIGN, null); // SUBMITTED 起始进 PROCESSING，PROCESSING/PENDING_INFO 保持
        TARGET.put(TicketAction.TRANSFER, null);
        TARGET.put(TicketAction.REQUEST_INFO, TicketStatus.PENDING_INFO);
        TARGET.put(TicketAction.SUPPLY, TicketStatus.PROCESSING);
        TARGET.put(TicketAction.RESOLVE, TicketStatus.RESOLVED);
        TARGET.put(TicketAction.CONFIRM, TicketStatus.CLOSED);
        TARGET.put(TicketAction.REJECT, TicketStatus.PROCESSING);
        TARGET.put(TicketAction.WITHDRAW, TicketStatus.WITHDRAWN);
        TARGET.put(TicketAction.CONFIRM_TIMEOUT, TicketStatus.CLOSED);
    }

    /**
     * 动作是否允许从指定状态发起。
     *
     * @param action 动作
     * @param status 当前状态
     * @return 允许与否
     */
    public static boolean canFire(TicketAction action, TicketStatus status) {
        Set<TicketStatus> allowed = ALLOWED_FROM.get(action);
        return allowed != null && allowed.contains(status);
    }

    /**
     * 动作的合法前置状态集（不可变）。
     *
     * @param action 动作
     * @return 前置状态集
     */
    public static Set<TicketStatus> allowedFrom(TicketAction action) {
        Set<TicketStatus> allowed = ALLOWED_FROM.get(action);
        return allowed == null ? Collections.emptySet() : Collections.unmodifiableSet(allowed);
    }

    /**
     * 动作目标状态（TRANSFER 保持原状态，返回 null 表示状态不变）。
     *
     * @param action 动作
     * @return 目标状态或 null
     */
    public static TicketStatus targetOf(TicketAction action) {
        return TARGET.get(action);
    }
}
