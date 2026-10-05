package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterKeyException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionPlan;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyGenerator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
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

    /**
     * 使用预构建执行计划执行限流，默认桥接到原有公共方法
     *
     * @param context     限流上下文
     * @param plan        请求执行计划
     * @param keyStrategy Key 生成策略
     * @return 限流检查结果
     */
    default SmartRedisLimiterResult tryAcquireWithResult(
            SmartRedisLimiterContext context,
            SmartRedisLimiterExecutionPlan plan,
            String keyStrategy) {
        return tryAcquireWithResult(
                context,
                plan.getLimits(),
                keyStrategy,
                plan.getFallback());
    }

    /**
     * 获取限流脚本
     *
     * @return Lua 脚本
     */
    DefaultRedisScript<List> getScript();

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

    /**
     * 降级处理（支持传入策略）
     *
     * @param fallbackStrategy 降级策略（可选）
     * @return true-放行，false-拒绝
     */
    default boolean handleFallback(String fallbackStrategy) {
        String strategy = fallbackStrategy;

        if (strategy == null || strategy.isEmpty()) {
            strategy = getProperties().getFallback().getOnRedisError();
        }

        SmartRedisLimiterFallbackStrategy fallback = SmartRedisLimiterFallbackStrategy.fromCode(strategy);

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
            throw new SmartRedisLimiterKeyException(
                    ErrorCode.KEY_GENERATOR_NOT_FOUND,
                    String.format(ErrorMessage.KEY_GENERATOR_NOT_FOUND, beanName), e);
        }
    }

    /**
     * 构建限流Key前缀。
     * 若上下文中已写入 PRECOMPUTED_KEY_PART（由自定义 KeyProvider 在拦截器中预计算），
     * 直接使用之；否则按 keyStrategy 解析 KeyGenerator。
     */
    default String buildBaseKey(SmartRedisLimiterContext context, String keyStrategy) {
        String keyPart = context.getAttribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART);
        if (keyPart == null || keyPart.isEmpty()) {
            SmartRedisLimiterKeyGenerator keyGenerator = getKeyGenerator(keyStrategy);
            keyPart = keyGenerator.generate(context);
        }
        return SmartRedisLimiterKeyHelper.buildBaseKey(getProperties().getMe(), keyPart);
    }
}
