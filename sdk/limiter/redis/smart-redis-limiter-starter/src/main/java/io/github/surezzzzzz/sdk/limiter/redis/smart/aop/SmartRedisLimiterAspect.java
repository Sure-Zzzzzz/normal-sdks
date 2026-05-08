package io.github.surezzzzzz.sdk.limiter.redis.smart.aop;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterKeyGenerator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterExecutor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

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

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private ApplicationContext applicationContext;

    @Around("@annotation(" + SmartRedisLimiterConstant.ANNOTATION_CLASS_NAME + ")")
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

        String fallbackStrategy = determineFallbackStrategy(limiter);

        boolean passed = executor.tryAcquire(context, limitRules, keyStrategy, fallbackStrategy);

        if (Boolean.TRUE.equals(properties.getAudit().getEnabled())) {
            if (!passed || Boolean.TRUE.equals(properties.getAudit().getLogOnPass())) {
                publishLimitEvent(context, limitRules, keyStrategy, passed, method);
            }
        }

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

    private void publishLimitEvent(SmartRedisLimiterContext context,
                                   List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                   String keyStrategy, boolean passed, Method method) {
        try {
            String limitKey = buildLimitKey(context, keyStrategy);
            SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                    this,
                    limitKey,
                    keyStrategy,
                    serializeLimitRules(limitRules),
                    passed,
                    SmartRedisLimiterConstant.SOURCE_ASPECT,
                    null,
                    null,
                    null,
                    null,
                    method.getName(),
                    method.toGenericString(),
                    context.getAttributes());
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("发布限流事件失败", e);
        }
    }

    private String buildLimitKey(SmartRedisLimiterContext context, String keyStrategy) {
        try {
            String beanName = SmartRedisLimiterKeyStrategy.getBeanName(keyStrategy);
            SmartRedisLimiterKeyGenerator generator = applicationContext.getBean(beanName, SmartRedisLimiterKeyGenerator.class);
            String keyPart = generator.generate(context);
            return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX
                    + properties.getMe()
                    + SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR
                    + keyPart;
        } catch (Exception e) {
            log.warn("构建限流Key失败, keyStrategy={}", keyStrategy, e);
            return null;
        }
    }

    private String serializeLimitRules(List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) {
        if (limitRules == null || limitRules.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limitRules.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            SmartRedisLimiterProperties.SmartLimitRule rule = limitRules.get(i);
            sb.append(rule.getCount())
                    .append("/")
                    .append(rule.getWindow())
                    .append(rule.getUnit().name().toLowerCase());
        }
        return sb.toString();
    }

    /**
     * 确定降级策略
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
