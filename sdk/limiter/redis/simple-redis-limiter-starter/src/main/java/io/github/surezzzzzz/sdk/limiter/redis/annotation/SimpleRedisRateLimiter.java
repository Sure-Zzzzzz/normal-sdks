package io.github.surezzzzzz.sdk.limiter.redis.annotation;

import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterComponent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple Redis 限流注解
 * <p>
 * 工作原理：
 * 1. 从全局 Token 桶中消耗一个令牌（所有方法共享同一个桶）
 * 2. 如果指定了 key，则将 key 存入 Set 集合进行去重检查
 * 3. Set 用于防止重复请求，而不是独立的限流配额
 * <p>
 * 使用示例：
 * <p>
 * // 示例 1：仅消耗令牌（不去重）
 *
 * @SimpleRedisRateLimiter public void simpleOperation() { }
 * <p>
 * // 示例 2：按用户去重
 * @SimpleRedisRateLimiter(key = "#userId")
 * public void userOperation(String userId) { }
 * <p>
 * // 示例 3：按订单去重
 * @SimpleRedisRateLimiter(key = "#orderId")
 * public void orderOperation(String orderId) { }
 * <p>
 * // 示例 4：组合去重（用户 + IP）
 * @SimpleRedisRateLimiter(key = "#userId + ':' + #ip")
 * public void sensitiveOperation(String userId, String ip) { }
 * <p>
 * // 示例 5：组合去重（用户 + 操作类型）
 * @SimpleRedisRateLimiter(key = "'user:' + #userId + ':action:' + #action")
 * public void actionOperation(String userId, String action) { }
 * <p>
 * 注意事项：
 * - Token 桶是全局共享的，所有方法消耗同一个桶的令牌
 * - key 用于 Set 去重，防止重复请求，而不是独立的限流配额
 * - 如果需要方法级独立配额，建议部署多个实例并使用不同的 `me` 标识
 * @author: Sure.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RedisLimiterComponent
public @interface SimpleRedisRateLimiter {

    /**
     * 限流标识，支持 SpEL 表达式
     * <p>
     * 说明：
     * - 空字符串（默认）：仅消耗令牌，不进行去重检查
     * - 固定值："api:createOrder"
     * - 方法参数："#userId" 或 "#request.orderId"
     * - 组合表达式："'user:' + #userId" 或 "#userId + ':' + #ip"
     * <p>
     * 用途：
     * - key 用于 Set 集合去重，防止重复请求
     * - 不是独立的限流配额，所有请求共享全局 Token 桶
     * <p>
     * 常见场景：
     * - 按用户去重：key = "#userId"
     * - 按订单去重：key = "#orderId"
     * - 按 IP 去重：key = "#request.remoteAddr"
     * - 用户 + IP 组合：key = "#userId + ':' + #ip"
     * - 用户 + 操作：key = "'user:' + #userId + ':action:' + #action"
     */
    String key() default "";

    /**
     * 是否使用哈希存储（适合长字符串）
     * <p>
     * 说明：
     * - false（默认）：直接存储原始 key
     * - true：存储 key.hashCode()，节省 Redis 内存
     * <p>
     * 建议：
     * - 短 key（< 50 字符）：使用 false
     * - 长 key（≥ 50 字符）：使用 true
     * <p>
     * 仅在 key 不为空时生效
     */
    boolean useHash() default false;

    /**
     * 限流失败时的错误消息
     * <p>
     * 说明：
     * - 当 Token 不足时：显示此消息
     * - 当检测到重复请求时：自动显示"请求正在处理中，请勿重复提交"
     */
    String message() default "系统繁忙，请稍后重试";

    /**
     * 限流失败时的处理策略
     * <p>
     * 可选值：
     * - EXCEPTION（默认）：抛出 RateLimitException 异常
     * - RETURN_NULL：返回 null（适用于返回值为对象类型的方法）
     * - CUSTOM：调用自定义降级方法
     */
    FallbackStrategy fallback() default FallbackStrategy.EXCEPTION;

    /**
     * 自定义降级方法名
     * <p>
     * 说明：
     * - 仅当 fallback = CUSTOM 时生效
     * - 默认为空：自动查找 "原方法名 + Fallback" 的方法
     * - 指定值：使用指定的方法名
     * <p>
     * 降级方法要求：
     * - 方法签名必须与原方法完全一致（参数类型和返回值）
     * - 必须在同一个类中
     * <p>
     * 示例：
     *
     * @SimpleRedisRateLimiter(fallback = CUSTOM, fallbackMethod = "handleLimit")
     * public Result process(String id) { ... }
     * <p>
     * public Result handleLimit(String id) {
     * return Result.fail("系统繁忙");
     * }
     */
    String fallbackMethod() default "";

    /**
     * 限流失败处理策略
     */
    enum FallbackStrategy {
        /**
         * 抛出 RateLimitException 异常
         * 适用于需要全局异常处理的场景
         */
        EXCEPTION,

        /**
         * 返回 null
         * 适用于返回值为对象类型的方法
         * 注意：基本类型（int、boolean 等）不适用
         */
        RETURN_NULL,

        /**
         * 调用自定义降级方法
         * 需要在同一个类中定义降级方法
         */
        CUSTOM
    }
}
