package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter 上下文属性键枚举
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterContextAttribute {

    /**
     * 请求路径
     */
    REQUEST_PATH("requestPath", "请求路径"),

    /**
     * 请求方法（HTTP Method）
     */
    REQUEST_METHOD("requestMethod", "请求方法"),

    /**
     * 客户端IP
     */
    CLIENT_IP("clientIp", "客户端IP"),

    /**
     * 匹配到的路径模式
     */
    MATCHED_PATH_PATTERN("matchedPathPattern", "匹配到的路径模式"),

    /**
     * 限流检查耗时（纳秒）
     */
    DURATION_NANOS("durationNanos", "限流检查耗时"),

    /**
     * 是否触发降级
     */
    FALLBACK("fallback", "是否触发降级"),

    /**
     * 降级策略
     */
    FALLBACK_STRATEGY("fallbackStrategy", "降级策略"),

    /**
     * 由自定义 KeyProvider 预先计算好的 key 片段（拦截器写入，算法/事件构建器读取）
     */
    PRECOMPUTED_KEY_PART("precomputedKeyPart", "自定义KeyProvider预计算的key片段");

    private final String code;
    private final String desc;

    /**
     * 根据代码获取枚举
     *
     * @param code 属性键代码
     * @return 枚举，如果不存在返回 null
     */
    public static SmartRedisLimiterContextAttribute fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmartRedisLimiterContextAttribute attr : values()) {
            if (attr.code.equals(code)) {
                return attr;
            }
        }
        return null;
    }

    /**
     * 判断代码是否有效
     *
     * @param code 属性键代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的代码
     *
     * @return 代码数组
     */
    public static String[] getAllCodes() {
        SmartRedisLimiterContextAttribute[] attrs = values();
        String[] codes = new String[attrs.length];
        for (int i = 0; i < attrs.length; i++) {
            codes[i] = attrs[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
