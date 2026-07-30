package io.github.surezzzzzz.sdk.crm.server.core.domain.audit;

import lombok.Getter;

/**
 * CRM 受控运行操作类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum CrmOperationType {

    /**
     * 领域事件、重放。
     */
    EVENT_REPLAY("event_replay", "领域事件、重放"),

    /**
     * 死信消息、重投。
     */
    DLT_REDRIVE("dlt_redrive", "死信消息、重投"),

    /**
     * 消费者、顺序、终止。
     */
    CONSUMER_SEQUENCE_TERMINATION("consumer_sequence_termination", "消费者、顺序、终止"),

    /**
     * 搜索、重建。
     */
    SEARCH_REBUILD("search_rebuild", "搜索、重建"),

    /**
     * 搜索、切换。
     */
    SEARCH_CUTOVER("search_cutover", "搜索、切换"),

    /**
     * 搜索、回滚。
     */
    SEARCH_ROLLBACK("search_rollback", "搜索、回滚"),

    /**
     * 消费者、安全、暂停。
     */
    CONSUMER_SECURITY_SUSPENSION("consumer_security_suspension", "消费者、安全、暂停"),

    /**
     * 消费者、端点、轮换。
     */
    CONSUMER_ENDPOINT_ROTATION("consumer_endpoint_rotation", "消费者、端点、轮换");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    CrmOperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static CrmOperationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CrmOperationType type : values()) {
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
        CrmOperationType[] types = values();
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
