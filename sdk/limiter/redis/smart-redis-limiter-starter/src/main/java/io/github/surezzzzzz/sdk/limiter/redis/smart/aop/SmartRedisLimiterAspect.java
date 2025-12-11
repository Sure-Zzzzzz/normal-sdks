package io.github.surezzzzzz.sdk.limiter.redis.smart.aop;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterExecutor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Sure.
 * @description 智能限流AOP切面
 * @Date: 2024/12/XX XX:XX
 */
@Aspect
@SmartRedisLimiterComponent
@Slf4j
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterAspect {

    @Autowired
    private SmartRedisLimiterExecutor executor;

    @Autowired
    private SmartRedisLimiterProperties properties;

    @Around("@annotation(io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!isAnnotationModeEnabled()) {
            log.debug("注解模式未启用，跳过限流");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        SmartRedisLimiter limiter = method.getAnnotation(SmartRedisLimiter.class);

        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder()
                .method(method)
                .args(joinPoint.getArgs())
                .target(joinPoint.getTarget())
                .build();

        String keyStrategy = limiter.keyStrategy();
        if (keyStrategy == null || keyStrategy.isEmpty()) {
            keyStrategy = properties.getAnnotation().getDefaultKeyStrategy();
        }

        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules = parseLimitRules(limiter);
        if (limitRules.isEmpty()) {
            limitRules = properties.getAnnotation().getDefaultLimits();
        }

        if (limitRules == null || limitRules.isEmpty()) {
            log.debug("无限流规则，直接执行方法: {}", method.getName());
            return joinPoint.proceed();
        }

        // ✅ 新增：获取降级策略
        String fallbackStrategy = determineFallbackStrategy(limiter);

        // ✅ 传递降级策略
        boolean passed = executor.tryAcquire(context, limitRules, keyStrategy, fallbackStrategy);

        if (!passed) {
            long retryAfter = limitRules.stream()
                    .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                    .min()
                    .orElse(1L);

            log.warn("注解限流触发: method={}, rules={}, retryAfter={}s",
                    method.getName(), limitRules, retryAfter);
            throw new SmartRedisLimitExceededException(method.getName(), retryAfter);
        }

        return joinPoint.proceed();
    }

    /**
     * ✅ 新增：确定降级策略
     * 优先级：注解级别 > 注解模式默认 > 全局默认
     */
    private String determineFallbackStrategy(SmartRedisLimiter limiter) {
        // 1. 注解级别
        if (limiter.fallback() != null && !limiter.fallback().isEmpty()) {
            log.debug("使用注解级别降级策略: {}", limiter.fallback());
            return limiter.fallback();
        }

        // 2. 注解模式默认值
        if (properties.getAnnotation().getDefaultFallback() != null &&
                !properties.getAnnotation().getDefaultFallback().isEmpty()) {
            log.debug("使用注解模式默认降级策略: {}", properties.getAnnotation().getDefaultFallback());
            return properties.getAnnotation().getDefaultFallback();
        }

        // 3. 全局默认值
        log.debug("使用全局降级策略: {}", properties.getFallback().getOnRedisError());
        return properties.getFallback().getOnRedisError();
    }

    private boolean isAnnotationModeEnabled() {
        if (!properties.getEnable()) {
            return false;
        }

        SmartRedisLimiterMode mode = SmartRedisLimiterMode.fromCode(properties.getMode());
        return mode.isAnnotationEnabled();
    }

    private List<SmartRedisLimiterProperties.SmartLimitRule> parseLimitRules(SmartRedisLimiter limiter) {
        List<SmartRedisLimiterProperties.SmartLimitRule> rules = new ArrayList<>();

        for (SmartRedisLimitRule limitRule : limiter.rules()) {
            SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
            rule.setCount(limitRule.count());
            rule.setWindow(limitRule.window());
            rule.setUnit(limitRule.unit());
            rules.add(rule);
        }

        return rules;
    }
}
