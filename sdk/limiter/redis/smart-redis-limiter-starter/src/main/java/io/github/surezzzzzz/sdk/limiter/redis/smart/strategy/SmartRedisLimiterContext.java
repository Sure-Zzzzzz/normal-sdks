package io.github.surezzzzzz.sdk.limiter.redis.smart.strategy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: Sure.
 * @description 智能Redis限流器上下文
 * @Date: 2024/12/XX XX:XX
 */
@Data
public class SmartRedisLimiterContext {

    /**
     * 方法信息（注解模式）
     */
    private Method method;

    /**
     * 方法参数
     */
    private Object[] args;

    /**
     * 目标对象
     */
    private Object target;

    /**
     * 扩展属性（存储所有上下文信息）
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(SmartRedisLimiterContextAttribute attribute) {
        return (T) attributes.get(attribute.getKey());
    }

    /**
     * 设置属性
     */
    public void setAttribute(SmartRedisLimiterContextAttribute attribute, Object value) {
        attributes.put(attribute.getKey(), value);
    }

    /**
     * 获取请求路径
     */
    public String getRequestPath() {
        return getAttribute(SmartRedisLimiterContextAttribute.REQUEST_PATH);
    }

    /**
     * 获取请求方法
     */
    public String getRequestMethod() {
        return getAttribute(SmartRedisLimiterContextAttribute.REQUEST_METHOD);
    }

    /**
     * 获取客户端IP
     */
    public String getClientIp() {
        return getAttribute(SmartRedisLimiterContextAttribute.CLIENT_IP);
    }

    /**
     * 获取匹配到的路径模式
     */
    public String getMatchedPathPattern() {
        return getAttribute(SmartRedisLimiterContextAttribute.MATCHED_PATH_PATTERN);
    }

    /**
     * 静态Builder
     */
    public static SmartRedisLimiterContextBuilder builder() {
        return new SmartRedisLimiterContextBuilder();
    }

    /**
     * Builder类
     */
    public static class SmartRedisLimiterContextBuilder {
        private final SmartRedisLimiterContext context = new SmartRedisLimiterContext();

        public SmartRedisLimiterContextBuilder method(Method method) {
            context.method = method;
            return this;
        }

        public SmartRedisLimiterContextBuilder args(Object[] args) {
            context.args = args;
            return this;
        }

        public SmartRedisLimiterContextBuilder target(Object target) {
            context.target = target;
            return this;
        }

        public SmartRedisLimiterContextBuilder attribute(SmartRedisLimiterContextAttribute attribute, Object value) {
            context.attributes.put(attribute.getKey(), value);
            return this;
        }

        public SmartRedisLimiterContextBuilder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                context.attributes.putAll(attributes);
            }
            return this;
        }

        public SmartRedisLimiterContext build() {
            return context;
        }
    }
}
