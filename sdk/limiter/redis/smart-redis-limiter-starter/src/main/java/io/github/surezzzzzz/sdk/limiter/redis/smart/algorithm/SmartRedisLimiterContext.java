package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 智能Redis限流器上下文
 * <p>承载限流检查所需的全部上下文信息，支持注解模式和拦截器模式</p>
 *
 * @author Sure.
 * @Date: 2026-05-08
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
     *
     * @param attribute 属性键
     * @param <T>      属性类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(SmartRedisLimiterContextAttribute attribute) {
        return (T) attributes.get(attribute.getCode());
    }

    /**
     * 设置属性
     *
     * @param attribute 属性键
     * @param value     属性值
     */
    public void setAttribute(SmartRedisLimiterContextAttribute attribute, Object value) {
        attributes.put(attribute.getCode(), value);
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

        /**
         * 设置方法信息
         *
         * @param method 方法
         * @return builder
         */
        public SmartRedisLimiterContextBuilder method(Method method) {
            context.method = method;
            return this;
        }

        /**
         * 设置方法参数
         *
         * @param args 方法参数
         * @return builder
         */
        public SmartRedisLimiterContextBuilder args(Object[] args) {
            context.args = args;
            return this;
        }

        /**
         * 设置目标对象
         *
         * @param target 目标对象
         * @return builder
         */
        public SmartRedisLimiterContextBuilder target(Object target) {
            context.target = target;
            return this;
        }

        /**
         * 设置上下文属性
         *
         * @param attribute 属性键
         * @param value     属性值
         * @return builder
         */
        public SmartRedisLimiterContextBuilder attribute(SmartRedisLimiterContextAttribute attribute, Object value) {
            context.attributes.put(attribute.getCode(), value);
            return this;
        }

        /**
         * 设置多个上下文属性
         *
         * @param attributes 属性映射
         * @return builder
         */
        public SmartRedisLimiterContextBuilder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                context.attributes.putAll(attributes);
            }
            return this;
        }

        /**
         * 构建上下文对象
         *
         * @return 上下文对象
         */
        public SmartRedisLimiterContext build() {
            return context;
        }
    }
}
