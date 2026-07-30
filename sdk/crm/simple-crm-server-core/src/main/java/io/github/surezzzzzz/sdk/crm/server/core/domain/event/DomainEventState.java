package io.github.surezzzzzz.sdk.crm.server.core.domain.event;

import lombok.Getter;

/**
 * 领域事件 Outbox 的权威投递状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum DomainEventState {

    /**
     * 待投递。
     */
    PENDING("pending", "待投递"),

    /**
     * 投递租约中。
     */
    LEASED("leased", "投递租约中"),

    /**
     * 已投递。
     */
    DELIVERED("delivered", "已投递"),

    /**
     * 死信、已进入死信队列。
     */
    DEAD_LETTERED("dead_lettered", "死信、已进入死信队列");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    DomainEventState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static DomainEventState fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DomainEventState type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断稳定代码是否有效。
     *
     * @param code 稳定类型代码
     * @return 有效时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部稳定类型代码。
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        DomainEventState[] types = values();
        String[] codes = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            codes[index] = types[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定类型代码。
     *
     * @return 稳定类型代码
     */
    /**
     * 返回稳定类型代码。
     *
     * @return 处理后的领域事实或校验结果。
     */
    @Override
    public String toString() {
        return code;
    }
}
