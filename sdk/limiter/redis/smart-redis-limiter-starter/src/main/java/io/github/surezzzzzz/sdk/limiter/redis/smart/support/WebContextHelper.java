package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterContext;

import javax.servlet.http.HttpServletRequest;

/**
 * @author: Sure.
 * @description Web上下文辅助类（纯工具类）
 * @Date: 2024/12/XX XX:XX
 */
public class WebContextHelper {

    private WebContextHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 填充Web上下文信息
     *
     * @param builder Context构建器
     * @param request HTTP请求
     */
    public static void fillWebContext(SmartRedisLimiterContext.SmartRedisLimiterContextBuilder builder,
                                      HttpServletRequest request) {
        builder.attribute(SmartRedisLimiterContextAttribute.REQUEST_PATH, getRequestPath(request));
        builder.attribute(SmartRedisLimiterContextAttribute.REQUEST_METHOD, request.getMethod());
        builder.attribute(SmartRedisLimiterContextAttribute.CLIENT_IP, getClientIp(request));
    }

    /**
     * 获取请求路径（去掉contextPath）
     */
    private static String getRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * 获取客户端真实IP
     */
    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多级代理取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
