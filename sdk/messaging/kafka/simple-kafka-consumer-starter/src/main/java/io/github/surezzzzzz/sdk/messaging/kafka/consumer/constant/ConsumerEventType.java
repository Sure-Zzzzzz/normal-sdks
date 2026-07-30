package io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant;

import lombok.Getter;

import java.util.Locale;

/**
 * 消费事件类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum ConsumerEventType {

    /**
     * 消费成功
     */
    CONSUMED("consumed", "消费成功"),

    /**
     * 触发重试
     */
    RETRY("retry", "触发重试"),

    /**
     * 死信投递
     */
    DEAD_LETTER("dead_letter", "死信投递"),

    /**
     * 幂等判定重复跳过
     */
    IDEMPOTENT_REJECT("idempotent_reject", "幂等判定重复跳过"),

    /**
     * 消费异常
     */
    ERROR("error", "消费异常");

    private final String code;
    private final String description;

    ConsumerEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，不存在返回 null
     */
    public static ConsumerEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.trim().toLowerCase(Locale.ROOT);
        for (ConsumerEventType type : values()) {
            if (type.code.equals(lowerCode)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        ConsumerEventType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
