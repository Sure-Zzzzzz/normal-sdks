package io.github.surezzzzzz.sdk.limiter.redis.smart.aop;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionCoordinator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionOutcome;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterEventHelper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能限流AOP切面
 * <p>处理 @SmartRedisLimiter 注解标记的方法，执行限流检查并发布事件</p>
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@Aspect
@SmartRedisLimiterComponent
@Slf4j
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmartRedisLimiterAspect {

    /**
     * 限流算法工厂
     */
    @Autowired
    private SmartRedisLimiterAlgorithmFactory algorithmFactory;

    /**
     * 统一请求执行协调器
     */
    @Autowired
    private SmartRedisLimiterExecutionCoordinator executionCoordinator;

    /**
     * 限流器配置
     */
    @Autowired
    private SmartRedisLimiterProperties properties;

    /**
     * 事件发布器
     */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * 环绕通知：拦截 @SmartRedisLimiter 注解标记的方法
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常或限流超限异常
     */
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
        SmartRedisLimiterAlgorithm algorithm = selectAlgorithm(limiter);
        SmartRedisLimiterExecutionOutcome outcome = executionCoordinator.execute(
                context,
                limitRules,
                keyStrategy,
                algorithm.getAlgorithm(),
                fallbackStrategy,
                limiter.resourceCode());
        SmartRedisLimiterResult result = outcome.getResult();
        limitRules = outcome.getPlan().getLimits();

        // 降级事件必须发布，正常通过事件由 logOnPass 控制
        if (!result.isPassed() || result.isFallback() || Boolean.TRUE.equals(properties.getLogOnPass())) {
            publishLimitEvent(context, limitRules, keyStrategy, algorithm.getAlgorithm(), result,
                    outcome.getPlan().getResourceCode(), outcome.getPlan().getPolicySource(),
                    outcome.getPlan().getPolicyRevision());
        }

        if (!result.isPassed()) {
            long retryAfter = limitRules.stream()
                    .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                    .min()
                    .orElse(1L);

            log.warn("注解限流触发: method={}, rules={}, retryAfter={}s",
                    method.getName(), limitRules, retryAfter);
            throw new SmartRedisLimitExceededException(method.getName(), retryAfter,
                    result.getLimit(), result.getRemaining(), result.getResetAt());
        }

        return joinPoint.proceed();
    }

    /**
     * 发布限流事件
     */
    private void publishLimitEvent(SmartRedisLimiterContext context,
                                   List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                   String keyStrategy, String algorithm, SmartRedisLimiterResult result,
                                   String resourceCode, String policySource, Long policyRevision) {
        try {
            SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                    this,
                    SmartRedisLimiterEventHelper.buildEventPayload(
                            context,
                            limitRules,
                            keyStrategy,
                            algorithm,
                            result,
                            SmartRedisLimiterConstant.SOURCE_ASPECT,
                            resourceCode,
                            policySource,
                            policyRevision));
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("发布限流事件失败", e);
        }
    }

    /**
     * 确定降级策略
     * 优先级：注解级别 > 注解模式默认 > 全局默认
     */
    private String determineFallbackStrategy(SmartRedisLimiter limiter) {
        if (limiter.fallback() != null && !limiter.fallback().isEmpty()) {
            log.debug("使用注解级别降级策略: {}", limiter.fallback());
            return limiter.fallback();
        }

        if (properties.getAnnotation().getDefaultFallback() != null &&
                !properties.getAnnotation().getDefaultFallback().isEmpty()) {
            log.debug("使用注解模式默认降级策略: {}", properties.getAnnotation().getDefaultFallback());
            return properties.getAnnotation().getDefaultFallback();
        }

        log.debug("使用全局降级策略: {}", properties.getFallback().getOnRedisError());
        return properties.getFallback().getOnRedisError();
    }

    /**
     * 判断注解模式是否启用
     */
    private boolean isAnnotationModeEnabled() {
        if (!properties.getEnable()) {
            return false;
        }

        SmartRedisLimiterMode mode = SmartRedisLimiterMode.fromCode(properties.getMode());
        return mode.isAnnotationEnabled();
    }

    /**
     * 选择限流算法
     */
    private SmartRedisLimiterAlgorithm selectAlgorithm(SmartRedisLimiter limiter) {
        return algorithmFactory.getAlgorithm(limiter.algorithm());
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
