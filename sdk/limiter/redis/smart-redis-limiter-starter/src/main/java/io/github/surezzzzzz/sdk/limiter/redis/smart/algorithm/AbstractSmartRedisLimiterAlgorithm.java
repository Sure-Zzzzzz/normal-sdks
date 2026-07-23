package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterRedisException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterScriptException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionPlan;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutionResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterTimeoutExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 限流算法抽象基类
 * 统一管理超时控制、降级处理、Redis Route 执行等公共逻辑
 *
 * @author Sure.
 * @Date: 2026-05-11
 */
@Slf4j
public abstract class AbstractSmartRedisLimiterAlgorithm implements SmartRedisLimiterAlgorithm {

    @Autowired
    private SmartRedisLimiterRedisExecutor redisExecutor;

    @Autowired
    private SmartRedisLimiterTimeoutExecutor timeoutExecutor;

    @Autowired
    private SmartRedisLimiterProperties properties;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Lua 限流脚本
     */
    private DefaultRedisScript<List> limiterScript;

    @PostConstruct
    public void init() {
        limiterScript = new DefaultRedisScript<>();
        limiterScript.setScriptText(getScriptText());
        limiterScript.setResultType(List.class);

        log.info("SmartRedisLimiter {} 初始化完成, Redis Route 强制启用, 超时控制: {}ms",
                getAlgorithm(), properties.getRedis().getCommandTimeout());
    }

    @Override
    public DefaultRedisScript<List> getScript() {
        return limiterScript;
    }

    @Override
    public SmartRedisLimiterProperties getProperties() {
        return properties;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public boolean tryAcquire(SmartRedisLimiterContext context,
                              List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                              String keyStrategy) {
        return tryAcquire(context, limitRules, keyStrategy, null);
    }

    @Override
    public boolean tryAcquire(SmartRedisLimiterContext context,
                              List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                              String keyStrategy,
                              String fallbackStrategy) {
        return tryAcquireWithResult(context, limitRules, keyStrategy, fallbackStrategy).isPassed();
    }

    @Override
    public SmartRedisLimiterResult tryAcquireWithResult(SmartRedisLimiterContext context,
                                                        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                        String keyStrategy,
                                                        String fallbackStrategy) {
        return executeWithResolvedBaseKey(
                context, limitRules, keyStrategy, fallbackStrategy, null);
    }

    /**
     * 使用预构建执行计划执行限流
     *
     * @param context     限流上下文
     * @param plan        请求执行计划
     * @param keyStrategy Key 生成策略
     * @return 限流检查结果
     */
    @Override
    public SmartRedisLimiterResult tryAcquireWithResult(
            SmartRedisLimiterContext context,
            SmartRedisLimiterExecutionPlan plan,
            String keyStrategy) {
        return executeWithResolvedBaseKey(
                context,
                plan.getLimits(),
                keyStrategy,
                plan.getFallback(),
                plan.getBaseKey());
    }

    private SmartRedisLimiterResult executeWithResolvedBaseKey(
            SmartRedisLimiterContext context,
            List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
            String keyStrategy,
            String fallbackStrategy,
            String prebuiltBaseKey) {
        long timeout = properties.getRedis().getCommandTimeout();
        long startTime = System.nanoTime();
        String routeKey;
        try {
            routeKey = prebuiltBaseKey == null
                    ? buildBaseKey(context, keyStrategy)
                    : prebuiltBaseKey;
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY, routeKey);
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED, true);
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED, false);
            context.setAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE, SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN);
        } catch (Exception e) {
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED, true);
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED, false);
            context.setAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE, SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN);
            log.error("SmartRedisLimiter {} 构建限流 Key 失败", getAlgorithm(), e);
            return buildFallbackResult(context, limitRules, startTime, fallbackStrategy,
                    SmartRedisLimiterConstant.FALLBACK_REASON_KEY_PROVIDER_ERROR);
        }

        FutureTask<SmartRedisLimiterResult> task = new FutureTask<>(() ->
                doExecuteWithResult(context, limitRules, keyStrategy, routeKey)
        );

        try {
            timeoutExecutor.execute(task);
            SmartRedisLimiterResult result = task.get(timeout, TimeUnit.MILLISECONDS);
            copyRouteContextFromResult(context, result);
            context.setAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS,
                    System.nanoTime() - startTime);
            return result;
        } catch (TimeoutException e) {
            task.cancel(true);
            log.warn("SmartRedisLimiter {} Redis操作超时({}ms)，触发降级策略", getAlgorithm(), timeout);
            return buildFallbackResult(context, limitRules, startTime, fallbackStrategy,
                    SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String fallbackReason = determineFallbackReason(cause);
            log.error("SmartRedisLimiter {} 执行异常, fallbackReason={}", getAlgorithm(), fallbackReason,
                    cause != null ? cause : e);
            copyRouteContextFromCause(context, cause);
            return buildFallbackResult(context, limitRules, startTime, fallbackStrategy, fallbackReason);
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            log.error("SmartRedisLimiter {} 执行被中断", getAlgorithm(), e);
            return buildFallbackResult(context, limitRules, startTime, fallbackStrategy,
                    SmartRedisLimiterConstant.FALLBACK_REASON_INTERRUPTED);
        } catch (RejectedExecutionException e) {
            log.error("SmartRedisLimiter {} 超时保护线程池拒绝任务", getAlgorithm(), e);
            return buildFallbackResult(context, limitRules, startTime, fallbackStrategy,
                    SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT);
        } catch (Exception e) {
            log.error("SmartRedisLimiter {} 执行异常", getAlgorithm(), e);
            return buildFallbackResult(context, limitRules, startTime, fallbackStrategy,
                    SmartRedisLimiterConstant.FALLBACK_REASON_UNKNOWN);
        }
    }

    /**
     * 通过 Redis Route 执行 Redis 操作
     *
     * @param routeKey routeKey
     * @param callback Redis 操作回调
     * @param <T>      返回值类型
     * @return 执行结果
     */
    protected <T> SmartRedisLimiterRedisExecutionResult<T> executeRedis(String routeKey,
                                                                        Function<StringRedisTemplate, T> callback) {
        return redisExecutor.execute(routeKey, callback);
    }

    /**
     * 构建窗口Key（统一处理 Hash Tag）
     *
     * @param baseKey       基础Key
     * @param windowSeconds 窗口秒数
     * @param windowSuffix  窗口后缀（如 "s" 或 "sw"）
     */
    protected String buildWindowKey(String baseKey, long windowSeconds, String windowSuffix) {
        return SmartRedisLimiterKeyHelper.buildWindowKey(baseKey, windowSeconds, windowSuffix,
                Boolean.TRUE.equals(properties.getRedis().getUseHashTag()));
    }

    private SmartRedisLimiterResult buildFallbackResult(SmartRedisLimiterContext context,
                                                        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                        long startTime,
                                                        String fallbackStrategy,
                                                        String fallbackReason) {
        boolean passed = handleFallback(fallbackStrategy);
        context.setAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS,
                System.nanoTime() - startTime);
        context.setAttribute(SmartRedisLimiterContextAttribute.FALLBACK, true);
        context.setAttribute(SmartRedisLimiterContextAttribute.FALLBACK_STRATEGY, fallbackStrategy);
        context.setAttribute(SmartRedisLimiterContextAttribute.FALLBACK_REASON, fallbackReason);

        long limit = limitRules.stream()
                .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getCount)
                .min()
                .orElse(0);
        long resetAt = System.currentTimeMillis() / SmartRedisLimiterConstant.MILLIS_PER_SECOND
                + limitRules.stream()
                .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                .min()
                .orElse(1);

        return SmartRedisLimiterResult.builder()
                .passed(passed)
                .limit(limit)
                .remaining(passed ? Math.max(limit - 1, 0) : 0)
                .resetAt(resetAt)
                .fallback(true)
                .fallbackReason(fallbackReason)
                .routeKey(context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY))
                .datasourceKey(context.getAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY))
                .redisMode(context.getAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE))
                .routeRequired(Boolean.TRUE.equals(context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED)))
                .routeResolved(Boolean.TRUE.equals(context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED)))
                .build();
    }

    private void copyRouteContextFromResult(SmartRedisLimiterContext context, SmartRedisLimiterResult result) {
        context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY, result.getRouteKey());
        context.setAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY, result.getDatasourceKey());
        context.setAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE, result.getRedisMode());
        context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED, result.isRouteRequired());
        context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED, result.isRouteResolved());
    }

    private void copyRouteContextFromCause(SmartRedisLimiterContext context, Throwable cause) {
        if (cause instanceof SmartRedisLimiterRedisException) {
            SmartRedisLimiterRedisException exception = (SmartRedisLimiterRedisException) cause;
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY, exception.getRouteKey());
            context.setAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY, exception.getDatasourceKey());
            context.setAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE, exception.getRedisMode());
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED, exception.isRouteRequired());
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED, exception.isRouteResolved());
            return;
        }
        if (cause instanceof SmartRedisLimiterScriptException) {
            SmartRedisLimiterScriptException exception = (SmartRedisLimiterScriptException) cause;
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY, exception.getRouteKey());
            context.setAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY, exception.getDatasourceKey());
            context.setAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE, exception.getRedisMode());
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED, exception.isRouteRequired());
            context.setAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED, exception.isRouteResolved());
        }
    }

    private String determineFallbackReason(Throwable cause) {
        if (cause instanceof SmartRedisLimiterRedisException) {
            SmartRedisLimiterRedisException exception = (SmartRedisLimiterRedisException) cause;
            return exception.getFallbackReason();
        }
        if (cause instanceof SmartRedisLimiterScriptException) {
            return SmartRedisLimiterConstant.FALLBACK_REASON_SCRIPT_ERROR;
        }
        return SmartRedisLimiterConstant.FALLBACK_REASON_UNKNOWN;
    }

    /**
     * 解析 Lua 返回的长整型字段
     *
     * @param value           字段值
     * @param fieldName       字段名称
     * @param executionResult Redis 执行结果
     * @return 长整型字段值
     */
    protected long parseScriptLong(Object value,
                                   String fieldName,
                                   SmartRedisLimiterRedisExecutionResult<?> executionResult) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                throw scriptException(ErrorCode.SCRIPT_RESULT_FIELD_INVALID,
                        String.format(ErrorMessage.SCRIPT_RESULT_FIELD_INVALID, fieldName),
                        executionResult, e);
            }
        }
        throw scriptException(ErrorCode.SCRIPT_RESULT_FIELD_INVALID,
                String.format(ErrorMessage.SCRIPT_RESULT_FIELD_INVALID, fieldName),
                executionResult, null);
    }

    /**
     * 构建带路由快照的脚本异常
     *
     * @param message         异常消息
     * @param executionResult Redis 执行结果
     * @return 脚本异常
     */
    protected SmartRedisLimiterScriptException scriptException(String message,
                                                               SmartRedisLimiterRedisExecutionResult<?> executionResult) {
        return scriptException(ErrorCode.SCRIPT_EXECUTION_FAILED, message, executionResult, null);
    }

    /**
     * 构建指定错误码且带路由快照的脚本异常
     *
     * @param errorCode       错误码
     * @param message         异常消息
     * @param executionResult Redis 执行结果
     * @return 脚本异常
     */
    protected SmartRedisLimiterScriptException scriptException(String errorCode,
                                                               String message,
                                                               SmartRedisLimiterRedisExecutionResult<?> executionResult) {
        return scriptException(errorCode, message, executionResult, null);
    }

    /**
     * 构建指定错误码且带路由快照和原始异常的脚本异常
     *
     * @param errorCode       错误码
     * @param message         异常消息
     * @param executionResult Redis 执行结果
     * @param cause           原始异常
     * @return 脚本异常
     */
    protected SmartRedisLimiterScriptException scriptException(String errorCode,
                                                               String message,
                                                               SmartRedisLimiterRedisExecutionResult<?> executionResult,
                                                               Throwable cause) {
        return new SmartRedisLimiterScriptException(
                errorCode,
                String.format(ErrorMessage.SCRIPT_EXECUTION_FAILED, message),
                cause,
                executionResult.getRouteKey(),
                executionResult.getDatasourceKey(),
                executionResult.getRedisMode(),
                executionResult.isRouteRequired(),
                executionResult.isRouteResolved());
    }

    /**
     * 子类实现：返回Lua脚本文本
     */
    protected abstract String getScriptText();

    /**
     * 子类实现：执行Redis限流检查并返回详细结果
     * Lua脚本应返回列表：[passed(1/0), limit, remaining, resetAt]
     */
    protected abstract SmartRedisLimiterResult doExecuteWithResult(SmartRedisLimiterContext context,
                                                                   List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                                   String keyStrategy,
                                                                   String baseKey);

}
