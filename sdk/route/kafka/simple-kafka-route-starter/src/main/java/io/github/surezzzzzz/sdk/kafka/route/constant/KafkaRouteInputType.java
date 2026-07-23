package io.github.surezzzzzz.sdk.kafka.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * Kafka 路由输入类型
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum KafkaRouteInputType {

    /**
     * 按 topic 路由
     */
    TOPIC("topic", "按 topic 路由"),

    /**
     * 按 route key 路由
     */
    ROUTE_KEY("route_key", "按 route key 路由"),

    /**
     * 显式 datasource
     */
    DATASOURCE("datasource", "显式 datasource");

    private final String code;
    private final String description;

    public static KafkaRouteInputType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.toLowerCase(Locale.ROOT).trim();
        for (KafkaRouteInputType type : values()) {
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
        KafkaRouteInputType[] types = values();
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
