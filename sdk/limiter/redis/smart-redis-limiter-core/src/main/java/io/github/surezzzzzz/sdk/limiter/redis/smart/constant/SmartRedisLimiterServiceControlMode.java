package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.Getter;

/**
 * SmartRedisLimiter 服务策略控制模式
 * <p>同一服务只能显式选择一种模式，不做按内容猜测的自动切换；
 * 模式与策略代次（policyEpoch）共同防止跨协议的陈旧数据互写。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum SmartRedisLimiterServiceControlMode {

    /**
     * 旧三元组单规则模式，使用 /v1/policy 契约
     */
    LEGACY_V1("LEGACY_V1", "旧三元组模式"),

    /**
     * 类型化多维门禁模式，使用 /v2/policy 契约
     */
    TYPED_V2("TYPED_V2", "类型化模式");

    /**
     * 模式编码（JSON 协议值）
     */
    private final String code;

    /**
     * 模式说明
     */
    private final String description;

    SmartRedisLimiterServiceControlMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按编码解析模式
     *
     * @param code 模式编码
     * @return 对应模式；未识别时返回 null，由调用方执行失败关闭
     */
    public static SmartRedisLimiterServiceControlMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmartRedisLimiterServiceControlMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * 判断编码是否为合法模式
     *
     * @param code 模式编码
     * @return 编码合法时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
