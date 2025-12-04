package io.github.surezzzzzz.sdk.limiter.redis.aspect;

import io.github.surezzzzzz.sdk.limiter.redis.SimpleRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.annotation.SimpleRedisRateLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.constant.TokenResult;
import io.github.surezzzzzz.sdk.limiter.redis.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;

/**
 * Simple Redis 限流注解切面 (Java 8 兼容版本)
 *
 * @author: Sure.
 */
@Slf4j
@Aspect
@RedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis", name = "enable", havingValue = "true")
public class SimpleRedisRateLimiterAspect {

    @Autowired
    private SimpleRedisLimiter limiter;

    @Autowired
    private RedisLimiterProperties redisLimiterProperties;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 启动时检查配置
     */
    @PostConstruct
    public void checkConfiguration() {
        log.info("=== SimpleRedisRateLimiter 注解切面初始化 ===");
        log.info("Token 桶启用状态: {}", redisLimiterProperties.getToken().isEnable());
        log.info("Set 桶启用状态: {}", redisLimiterProperties.getSet().isEnable());

        if (!redisLimiterProperties.getToken().isEnable()) {
            log.warn("警告：Token 桶未启用！使用 @SimpleRedisRateLimiter 注解可能会失败");
            log.warn("请在配置文件中设置: io.github.surezzzzzz.sdk.limiter.redis.token.enable=true");
        }
    }

    @Around("@annotation(rateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint, SimpleRedisRateLimiter rateLimiter) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringTypeName() + "." + signature.getName();

        try {
            // 1. 配置检查
            String key = rateLimiter.key();
            if (key != null && !key.trim().isEmpty()) {
                // 如果指定了 key，需要 Set 桶
                if (!redisLimiterProperties.getSet().isEnable()) {
                    log.error("配置错误: 方法 {} 使用了 key=\"{}\", 但 Set 桶未启用", methodName, key);
                    log.error("请在配置文件中设置: io.github.surezzzzzz.sdk.limiter.redis.set.enable=true");
                    throw new IllegalStateException(
                            "Set 桶未启用，无法使用带 key 的限流功能。" +
                                    "请设置 io.github.surezzzzzz.sdk.limiter.redis.set.enable=true"
                    );
                }
            }

            if (!redisLimiterProperties.getToken().isEnable()) {
                log.error("配置错误: 方法 {} 使用了限流注解, 但 Token 桶未启用", methodName);
                log.error("请在配置文件中设置: io.github.surezzzzzz.sdk.limiter.redis.token.enable=true");
                throw new IllegalStateException(
                        "Token 桶未启用，无法使用限流功能。" +
                                "请设置 io.github.surezzzzzz.sdk.limiter.redis.token.enable=true"
                );
            }

            // 2. 解析限流 Key
            String parsedKey = parseKey(key, joinPoint);

            // 3. 执行限流检查
            TokenResult result = checkRateLimit(parsedKey, rateLimiter.useHash());

            // 4. 根据结果处理
            if (result == TokenResult.SUCCESS) {
                log.debug("限流检查通过: method={}, key={}", methodName, parsedKey);
                return joinPoint.proceed();
            } else {
                log.warn("限流触发: method={}, key={}, result={}", methodName, parsedKey, result);
                return handleRateLimitExceeded(rateLimiter, joinPoint, result);
            }
        } catch (RateLimitException e) {
            // 限流异常直接抛出
            throw e;
        } catch (IllegalStateException e) {
            // 配置错误直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常记录日志后继续执行业务逻辑（降级策略）
            log.error("限流检查异常，放行请求: method={}", methodName, e);
            return joinPoint.proceed();
        }
    }

    /**
     * 解析限流 Key（支持 SpEL 表达式）
     */
    private String parseKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        // 如果 key 为空，表示仅消耗令牌，不存储标识
        if (keyExpression == null || keyExpression.trim().isEmpty()) {
            return "";
        }

        // 如果不包含 SpEL 表达式符号，直接返回
        if (!keyExpression.contains("#") && !keyExpression.contains("'")) {
            return keyExpression;
        }

        // 解析 SpEL 表达式
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();

            EvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, args, nameDiscoverer
            );

            Expression expression = parser.parseExpression(keyExpression);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.error("解析限流 Key 失败，使用原始表达式: keyExpression={}", keyExpression, e);
            return keyExpression;
        }
    }

    /**
     * 执行限流检查
     */
    private TokenResult checkRateLimit(String key, boolean useHash) {
        try {
            if (key.isEmpty()) {
                // 仅消耗令牌，不存储标识
                boolean success = limiter.getToken();
                return success ? TokenResult.SUCCESS : TokenResult.INSUFFICIENT;
            } else {
                // 令牌 + 去重
                int resultCode = limiter.getToken(key, useHash);
                return TokenResult.fromCode((long) resultCode);
            }
        } catch (Exception e) {
            log.error("限流检查执行异常: key={}", key, e);
            // 异常时返回成功，避免限流器故障影响业务
            return TokenResult.SUCCESS;
        }
    }

    /**
     * 处理限流超限情况
     */
    private Object handleRateLimitExceeded(SimpleRedisRateLimiter rateLimiter,
                                           ProceedingJoinPoint joinPoint,
                                           TokenResult result) throws Throwable {
        String message = buildErrorMessage(rateLimiter.message(), result);
        int resultCode = result.getCode();

        SimpleRedisRateLimiter.FallbackStrategy strategy = rateLimiter.fallback();

        if (strategy == SimpleRedisRateLimiter.FallbackStrategy.EXCEPTION) {
            throw new RateLimitException(message, resultCode);
        } else if (strategy == SimpleRedisRateLimiter.FallbackStrategy.RETURN_NULL) {
            log.debug("限流失败，返回 null");
            return null;
        } else if (strategy == SimpleRedisRateLimiter.FallbackStrategy.CUSTOM) {
            return invokeFallbackMethod(joinPoint, rateLimiter.fallbackMethod());
        } else {
            throw new RateLimitException(message, resultCode);
        }
    }

    /**
     * 构建错误消息
     */
    private String buildErrorMessage(String baseMessage, TokenResult result) {
        if (result == TokenResult.INSUFFICIENT) {
            return baseMessage;
        } else if (result == TokenResult.EXISTS) {
            return "请求正在处理中，请勿重复提交";
        } else {
            return "限流失败: " + result.getMessage();
        }
    }

    /**
     * 调用自定义降级方法
     */
    private Object invokeFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackMethodName) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 如果未指定降级方法名，默认为：原方法名 + "Fallback"
        if (fallbackMethodName == null || fallbackMethodName.trim().isEmpty()) {
            fallbackMethodName = method.getName() + "Fallback";
        }

        try {
            Method fallbackMethod = joinPoint.getTarget().getClass()
                    .getMethod(fallbackMethodName, method.getParameterTypes());
            log.debug("调用降级方法: {}", fallbackMethodName);
            return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            log.error("未找到降级方法: {}", fallbackMethodName);
            throw new RateLimitException("系统繁忙，请稍后重试");
        }
    }
}
