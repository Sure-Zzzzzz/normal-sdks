package io.github.surezzzzzz.sdk.limiter.redis.smart.generator;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;

import javax.servlet.http.HttpServletRequest;

/**
 * 自定义限流 Key Provider
 * <p>允许用户从 HttpServletRequest、SmartRedisLimiterContext 或任意外部上下文
 * （SecurityContextHolder、ThreadLocal 等）提取限流 key。
 * 当拦截器规则中指定了 key-provider 时，优先级高于 key-strategy。</p>
 *
 * <p><b>实现要求</b>：实现类必须是线程安全的（拦截器为单例，provide 方法会被并发调用）。</p>
 *
 * @author surezzzzzz
 */
@FunctionalInterface
public interface SmartRedisLimiterKeyProvider {

    /**
     * 提取限流 key
     *
     * @param request HTTP 请求（拦截器模式下保证非 null，可直接读 header/param/cookie/attribute）
     * @param context 限流上下文（含 matchedPathPattern / requestMethod / clientIp 等已解析字段）
     * @return 限流 key 前缀，如 "client:AKP1234567890"。返回 null 或空字符串时回退到 key-strategy
     */
    String provide(HttpServletRequest request, SmartRedisLimiterContext context);
}
