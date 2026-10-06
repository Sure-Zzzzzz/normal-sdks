package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.Getter;

/**
 * SmartRedisLimiter 类型化限流计数维度
 * <p>维度回答“额度归谁使用”，与算法（固定窗口/滑动窗口）互不替代；
 * 其中 CUSTOMER 是唯一与 License 同源消费客户绑定事实的商业配额维度。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum SmartRedisLimiterDataDimension {

    /**
     * 资源共享总量：一个服务内某资源的所有调用者共用同一额度
     */
    RESOURCE("RESOURCE", "资源共享总量"),

    /**
     * 客户端 IP：经可信入口清洗并规范化的数字地址
     */
    IP("IP", "客户端地址"),

    /**
     * 人员：同一人员主体共享的用户额度
     */
    USER("USER", "人员主体"),

    /**
     * 服务：同一逻辑服务主体的共享额度
     */
    SERVICE("SERVICE", "服务主体"),

    /**
     * 凭据：每对访问凭据的独立额度
     */
    CREDENTIAL("CREDENTIAL", "访问凭据"),

    /**
     * 客户：显式绑定的部门根及子树在同一资源内的合计额度
     */
    CUSTOMER("CUSTOMER", "客户配额"),

    /**
     * 自定义：宿主声明的业务类型及稳定对象
     */
    CUSTOM("CUSTOM", "自定义业务对象");

    /**
     * 维度编码（JSON 协议值）
     */
    private final String code;

    /**
     * 维度说明
     */
    private final String description;

    SmartRedisLimiterDataDimension(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按编码解析维度
     *
     * @param code 维度编码
     * @return 对应维度；未识别时返回 null，由调用方执行失败关闭
     */
    public static SmartRedisLimiterDataDimension fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmartRedisLimiterDataDimension dimension : values()) {
            if (dimension.code.equals(code)) {
                return dimension;
            }
        }
        return null;
    }

    /**
     * 判断编码是否为合法维度
     *
     * @param code 维度编码
     * @return 编码合法时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
