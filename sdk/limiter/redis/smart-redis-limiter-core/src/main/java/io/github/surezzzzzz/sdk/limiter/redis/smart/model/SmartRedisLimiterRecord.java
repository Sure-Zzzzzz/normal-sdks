package io.github.surezzzzzz.sdk.limiter.redis.smart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SmartRedisLimiter 限流审计记录
 *
 * <p>事件监听器将 {@link io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent}
 * 转换为 Record 后，交给 Handler 处理。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartRedisLimiterRecord {

    // ==================== 用户信息（来自 Provider）====================

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端类型
     */
    private String clientType;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    // ==================== 限流上下文 ====================

    /**
     * 限流 Key
     */
    private String limitKey;

    /**
     * 限流策略代码（Key生成策略：method/path/ip/path-pattern）
     */
    private String keyStrategy;

    /**
     * 限流算法类型（fixed/sliding）
     */
    private String algorithm;

    /**
     * 限流规则（JSON 字符串）
     */
    private String limitRules;

    /**
     * 限流结果：true=通过，false=触发限流
     */
    private boolean passed;

    // ==================== 来源信息 ====================

    /**
     * 来源：INTERCEPTOR / ASPECT
     */
    private String source;

    // ==================== 请求信息（仅 Interceptor 模式）====================

    /**
     * 请求 URI
     */
    private String requestUri;

    /**
     * HTTP 方法
     */
    private String httpMethod;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 匹配到的路径模式
     */
    private String matchedPathPattern;

    // ==================== 方法信息（仅 Aspect 模式）====================

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 方法全限定名
     */
    private String methodQualifiedName;

    // ==================== 元数据 ====================

    /**
     * 事件时间戳
     */
    private Long timestamp;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 扩展字段
     */
    private Map<String, String> extra;
}
