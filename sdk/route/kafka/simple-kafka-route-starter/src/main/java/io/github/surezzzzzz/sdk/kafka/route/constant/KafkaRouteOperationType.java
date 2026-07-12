package io.github.surezzzzzz.sdk.kafka.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Kafka 路由操作类型
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum KafkaRouteOperationType {

    /**
     * 发送消息
     */
    SEND("send", "发送消息"),

    /**
     * 获取 KafkaTemplate
     */
    TEMPLATE("template", "获取 KafkaTemplate"),

    /**
     * 回调执行
     */
    EXECUTE("execute", "回调执行"),

    /**
     * 获取客户端工厂
     */
    FACTORY("factory", "获取客户端工厂");

    private final String code;
    private final String description;

    public static KafkaRouteOperationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.toLowerCase().trim();
        for (KafkaRouteOperationType type : values()) {
            if (type.code.equals(lowerCode)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        KafkaRouteOperationType[] types = values();
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
