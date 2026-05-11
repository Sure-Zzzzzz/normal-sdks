package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 固定窗口限流算法实现
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterFixedWindowAlgorithm implements SmartRedisLimiterAlgorithm {

    private static final String LIMITER_SCRIPT =
            "local key_count = #KEYS\n" +
                    "for i = 1, key_count do\n" +
                    "    local current = redis.call('GET', KEYS[i])\n" +
                    "    if current and tonumber(current) <= 0 then\n" +
                    "        return 0\n" +
                    "    end\n" +
                    "end\n" +
                    "local arg_idx = 1\n" +
                    "for i = 1, key_count do\n" +
                    "    local limit = tonumber(ARGV[arg_idx])\n" +
                    "    local window = tonumber(ARGV[arg_idx + 1])\n" +
                    "    arg_idx = arg_idx + 2\n" +
                    "    local current = redis.call('GET', KEYS[i])\n" +
                    "    if not current then\n" +
                    "        redis.call('SET', KEYS[i], limit - 1, 'EX', window)\n" +
                    "    else\n" +
                    "        redis.call('DECR', KEYS[i])\n" +
                    "    end\n" +
                    "end\n" +
                    "return 1";

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SmartRedisLimiterProperties properties;

    @Autowired
    private ApplicationContext applicationContext;

    private DefaultRedisScript<Long> limiterScript;
    private boolean isClusterMode = false;

    /**
     * 单线程计时器（用于超时控制）
     */
    private ScheduledExecutorService timeoutScheduler;

    @PostConstruct
    public void init() {
        limiterScript = new DefaultRedisScript<>();
        limiterScript.setScriptText(LIMITER_SCRIPT);
        limiterScript.setResultType(Long.class);

        isClusterMode = detectClusterMode();

        timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SmartRedisLimiter-Timer");
            thread.setDaemon(true);
            return thread;
        });

        log.info("SmartRedisLimiter 初始化完成, 集群模式: {}, 超时控制: {}ms",
                isClusterMode,
                properties.getRedis().getCommandTimeout());
    }

    @Override
    public String getAlgorithm() {
        return SmartRedisLimiterConstant.ALGORITHM_FIXED;
    }

    @Override
    public DefaultRedisScript<Long> getScript() {
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

    @PreDestroy
    public void destroy() {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdown();
            log.info("SmartRedisLimiter 计时器已关闭");
        }
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
        long timeout = properties.getRedis().getCommandTimeout();

        FutureTask<Boolean> task = new FutureTask<>(() ->
                executeRedisLimit(context, limitRules, keyStrategy)
        );

        timeoutScheduler.execute(task);

        try {
            return task.get(timeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            task.cancel(true);
            log.warn("SmartRedisLimiter Redis操作超时({}ms)，触发降级策略", timeout);
            return handleFallback(e, fallbackStrategy);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("SmartRedisLimiter 执行异常", cause != null ? cause : e);
            return handleFallback(cause != null ? (Exception) cause : e, fallbackStrategy);

        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            log.error("SmartRedisLimiter 执行被中断", e);
            return handleFallback(e, fallbackStrategy);

        } catch (Exception e) {
            log.error("SmartRedisLimiter 执行异常", e);
            return handleFallback(e, fallbackStrategy);
        }
    }

    /**
     * 实际执行Redis限流检查
     */
    private boolean executeRedisLimit(SmartRedisLimiterContext context,
                                      List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                      String keyStrategy) {
        String baseKey = buildBaseKey(context, keyStrategy);

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (SmartRedisLimiterProperties.SmartLimitRule rule : limitRules) {
            String windowKey = buildWindowKey(baseKey, rule.getWindowSeconds());
            keys.add(windowKey);
            args.add(String.valueOf(rule.getCount()));
            args.add(String.valueOf(rule.getWindowSeconds()));
        }

        Long result = redisTemplate.execute(limiterScript, keys, args.toArray());

        boolean passed = result != null && result == 1L;

        if (!passed) {
            log.warn("SmartRedisLimiter 限流触发: key={}, rules={}", baseKey, limitRules);
        } else {
            log.debug("SmartRedisLimiter 限流通过: key={}", baseKey);
        }

        return passed;
    }

    /**
     * 构建窗口Key
     */
    String buildWindowKey(String baseKey, long windowSeconds) {
        String windowSuffix = SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR +
                windowSeconds +
                SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS;

        if (isClusterMode) {
            return SmartRedisLimiterRedisKeyConstant.HASH_TAG_LEFT +
                    baseKey +
                    SmartRedisLimiterRedisKeyConstant.HASH_TAG_RIGHT +
                    windowSuffix;
        } else {
            return baseKey + windowSuffix;
        }
    }
}
