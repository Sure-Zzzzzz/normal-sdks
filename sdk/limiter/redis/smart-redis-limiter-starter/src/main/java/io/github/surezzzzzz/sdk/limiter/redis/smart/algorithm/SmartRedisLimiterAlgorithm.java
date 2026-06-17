package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * 限流算法接口
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
public interface SmartRedisLimiterAlgorithm {

    Logger log = LoggerFactory.getLogger(SmartRedisLimiterAlgorithm.class);

    /**
     * 获取算法标识
     *
     * @return 算法标识，如 fixed、sliding
     */
    String getAlgorithm();

    /**
     * 执行限流检查
     *
     * @param context     限流上下文
     * @param limitRules  限流规则列表
     * @param keyStrategy Key生成策略
     * @return true-允许通过，false-触发限流
     */
    boolean tryAcquire(SmartRedisLimiterContext context,
                       List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                       String keyStrategy);

    /**
     * 执行限流检查（带降级策略）
     *
     * @param context          限流上下文
     * @param limitRules       限流规则列表
     * @param keyStrategy      Key生成策略
     * @param fallbackStrategy 降级策略（可选，为null时使用全局配置）
     * @return true-允许通过，false-触发限流
     */
    boolean tryAcquire(SmartRedisLimiterContext context,
                       List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                       String keyStrategy,
                       String fallbackStrategy);

    /**
     * 执行限流检查并返回详细结果（用于响应头等场景）
     *
     * @param context          限流上下文
     * @param limitRules       限流规则列表
     * @param keyStrategy      Key生成策略
     * @param fallbackStrategy 降级策略（可选，为null时使用全局配置）
     * @return 限流检查结果（包含 passed / limit / remaining / resetAt）
     */
    SmartRedisLimiterResult tryAcquireWithResult(SmartRedisLimiterContext context,
                                                 List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                 String keyStrategy,
                                                 String fallbackStrategy);

    // ==================== Accessors (implements must provide) ====================

    /**
     * 获取限流脚本
     *
     * @return Lua 脚本
     */
    DefaultRedisScript<List> getScript();

    /**
     * 获取 RedisTemplate
     *
     * @return RedisTemplate
     */
    RedisTemplate<String, String> getRedisTemplate();

    /**
     * 获取 Properties
     *
     * @return SmartRedisLimiterProperties
     */
    SmartRedisLimiterProperties getProperties();

    /**
     * 获取 ApplicationContext
     *
     * @return ApplicationContext
     */
    ApplicationContext getApplicationContext();

    // ==================== Default Methods ====================

    /**
     * 检测Redis是否为集群模式
     */
    default boolean detectClusterMode() {
        try {
            RedisConnectionFactory connectionFactory = getRedisTemplate().getConnectionFactory();
            if (connectionFactory == null) {
                return false;
            }

            try (RedisClusterConnection clusterConnection = connectionFactory.getClusterConnection()) {
                return clusterConnection != null;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            log.debug("检测Redis集群模式失败，默认为单机模式", e);
            return false;
        }
    }

    /**
     * 降级处理（支持传入策略）
     *
     * @param e                异常信息
     * @param fallbackStrategy 降级策略（可选）
     * @return true-放行，false-拒绝
     */
    default boolean handleFallback(Exception e, String fallbackStrategy) {
        String strategy = fallbackStrategy;

        if (strategy == null || strategy.isEmpty()) {
            strategy = getProperties().getFallback().getOnRedisError();
        }

        SmartRedisLimiterFallbackStrategy fallback =
                SmartRedisLimiterFallbackStrategy.fromCode(strategy);

        if (fallback == SmartRedisLimiterFallbackStrategy.DENY) {
            log.warn("SmartRedisLimiter Redis异常，降级策略: {} - 拒绝请求", fallback.getDesc());
            return false;
        } else {
            log.warn("SmartRedisLimiter Redis异常，降级策略: {} - 放行请求", fallback.getDesc());
            return true;
        }
    }

    /**
     * 获取Key生成器
     */
    default SmartRedisLimiterKeyGenerator getKeyGenerator(String strategyCode) {
        if (strategyCode == null || strategyCode.isEmpty()) {
            strategyCode = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        }

        String beanName = SmartRedisLimiterKeyStrategy.getBeanName(strategyCode);

        try {
            return getApplicationContext().getBean(beanName, SmartRedisLimiterKeyGenerator.class);
        } catch (Exception e) {
            log.error("SmartRedisLimiter 无法获取KeyGenerator: {}", beanName, e);
            throw new IllegalArgumentException(SmartRedisLimiterConstant.MSG_KEY_GENERATOR_NOT_FOUND + beanName);
        }
    }

    /**
     * 构建限流Key前缀
     * <p>若上下文中已写入 {@link SmartRedisLimiterContextAttribute#PRECOMPUTED_KEY_PART}
     * （由自定义 KeyProvider 在拦截器中预计算），直接使用之；否则按 keyStrategy 解析 KeyGenerator。</p>
     */
    default String buildBaseKey(SmartRedisLimiterContext context, String keyStrategy) {
        String keyPart = context.getAttribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART);
        if (keyPart == null || keyPart.isEmpty()) {
            SmartRedisLimiterKeyGenerator keyGenerator = getKeyGenerator(keyStrategy);
            keyPart = keyGenerator.generate(context);
        }
        return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX +
                getProperties().getMe() +
                SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR +
                keyPart;
    }
}
