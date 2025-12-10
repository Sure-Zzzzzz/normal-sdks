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
        // 检查是否启用注解模式
        if (!isAnnotationModeEnabled()) {
            log.debug("注解模式未启用，跳过限流");
            return joinPoint.proceed();
        }

        // 获取方法和注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        SmartRedisLimiter limiter = method.getAnnotation(SmartRedisLimiter.class);

        // 构建限流上下文（注解模式只需要方法信息）
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder()
                .method(method)
                .args(joinPoint.getArgs())
                .target(joinPoint.getTarget())
                .build();

        // 获取限流配置
        String keyStrategy = limiter.keyStrategy();
        if (keyStrategy == null || keyStrategy.isEmpty()) {
            keyStrategy = properties.getAnnotation().getDefaultKeyStrategy();
        }

        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules = parseLimitRules(limiter);
        if (limitRules.isEmpty()) {
            limitRules = properties.getAnnotation().getDefaultLimits();
        }

        // 如果没有限流规则，直接执行
        if (limitRules == null || limitRules.isEmpty()) {
            log.debug("无限流规则，直接执行方法: {}", method.getName());
            return joinPoint.proceed();
        }

        // 执行限流检查
        boolean passed = executor.tryAcquire(context, limitRules, keyStrategy);

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
     * 检查注解模式是否启用
     */
    private boolean isAnnotationModeEnabled() {
        if (!properties.getEnable()) {
            return false;
        }

        SmartRedisLimiterMode mode = SmartRedisLimiterMode.fromCode(properties.getMode());
        return mode.isAnnotationEnabled();
    }

    /**
     * 解析注解中的限流规则
     */
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
