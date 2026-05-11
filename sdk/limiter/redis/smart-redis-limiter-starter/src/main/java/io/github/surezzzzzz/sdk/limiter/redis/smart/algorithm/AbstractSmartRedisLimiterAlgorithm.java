package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.*;

/**
 * 限流算法抽象基类
 * 统一管理超时控制、降级处理、集群模式检测、RedisTemplate 等公共逻辑
 *
 * @author Sure.
 * @Date: 2026-05-11
 */
@Slf4j
public abstract class AbstractSmartRedisLimiterAlgorithm implements SmartRedisLimiterAlgorithm {

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SmartRedisLimiterProperties properties;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Lua 限流脚本
     */
    private DefaultRedisScript<List> limiterScript;

    /**
     * 是否集群模式
     */
    protected boolean isClusterMode = false;

    /**
     * 单线程调度器（用于 Redis 命令超时控制）
     */
    private ScheduledExecutorService timeoutScheduler;

    @PostConstruct
    public void init() {
        limiterScript = new DefaultRedisScript<>();
        limiterScript.setScriptText(getScriptText());
        limiterScript.setResultType(List.class);

        isClusterMode = detectClusterMode();

        timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SmartRedisLimiter-" + getAlgorithm() + "-Timer");
            thread.setDaemon(true);
            return thread;
        });

        log.info("SmartRedisLimiter {} 初始化完成, 集群模式: {}, 超时控制: {}ms",
                getAlgorithm(), isClusterMode, properties.getRedis().getCommandTimeout());
    }

    @PreDestroy
    public void destroy() {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdown();
            log.info("SmartRedisLimiter {} 计时器已关闭", getAlgorithm());
        }
    }

    @Override
    public DefaultRedisScript<List> getScript() {
        return limiterScript;
    }

    @Override
    public RedisTemplate<String, String> getRedisTemplate() {
        return redisTemplate;
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
        long timeout = properties.getRedis().getCommandTimeout();

        FutureTask<SmartRedisLimiterResult> task = new FutureTask<>(() ->
                doExecuteWithResult(context, limitRules, keyStrategy)
        );

        timeoutScheduler.execute(task);

        try {
            return task.get(timeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            task.cancel(true);
            log.warn("SmartRedisLimiter {} Redis操作超时({}ms)，触发降级策略", getAlgorithm(), timeout);
            boolean passed = handleFallback(e, fallbackStrategy);
            return buildFallbackResult(passed, limitRules);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("SmartRedisLimiter {} 执行异常", getAlgorithm(), cause != null ? cause : e);
            boolean passed = handleFallback(cause != null ? (Exception) cause : e, fallbackStrategy);
            return buildFallbackResult(passed, limitRules);

        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            log.error("SmartRedisLimiter {} 执行被中断", getAlgorithm(), e);
            boolean passed = handleFallback(e, fallbackStrategy);
            return buildFallbackResult(passed, limitRules);

        } catch (Exception e) {
            log.error("SmartRedisLimiter {} 执行异常", getAlgorithm(), e);
            boolean passed = handleFallback(e, fallbackStrategy);
            return buildFallbackResult(passed, limitRules);
        }
    }

    /**
     * 降级时构建 Result（无法获取 Redis 中的精确数据，remaining/resetAt 为估算值）
     */
    private SmartRedisLimiterResult buildFallbackResult(boolean passed,
                                                        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) {
        long limit = limitRules.stream()
                .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getCount)
                .min()
                .orElse(0);
        long resetAt = System.currentTimeMillis() / 1000
                + limitRules.stream()
                .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                .min()
                .orElse(1);

        return SmartRedisLimiterResult.builder()
                .passed(passed)
                .limit(limit)
                .remaining(passed ? limit - 1 : 0)
                .resetAt(resetAt)
                .build();
    }

    /**
     * 构建窗口Key（统一处理集群模式 HashTag）
     *
     * @param baseKey       基础Key
     * @param windowSeconds 窗口秒数
     * @param windowSuffix  窗口后缀（如 "s" 或 "sw"）
     */
    protected String buildWindowKey(String baseKey, long windowSeconds, String windowSuffix) {
        String suffix = SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR + windowSeconds + windowSuffix;

        if (isClusterMode) {
            return SmartRedisLimiterRedisKeyConstant.HASH_TAG_LEFT
                    + baseKey
                    + SmartRedisLimiterRedisKeyConstant.HASH_TAG_RIGHT
                    + suffix;
        } else {
            return baseKey + suffix;
        }
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
                                                                   String keyStrategy);
}
