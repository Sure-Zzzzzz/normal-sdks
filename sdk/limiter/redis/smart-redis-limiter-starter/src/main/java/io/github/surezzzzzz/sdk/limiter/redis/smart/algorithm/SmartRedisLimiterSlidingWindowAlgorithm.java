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
 * 滑动窗口限流算法实现
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterSlidingWindowAlgorithm implements SmartRedisLimiterAlgorithm {

    // Lua脚本中 window 是纳秒（= windowSeconds * NANOSECONDS_PER_SECOND），current_time 是纳秒，member 是字符串
    private static final String LIMITER_SCRIPT =
            "local key_count = #KEYS\n" +
                    "local current_time = tonumber(ARGV[#ARGV])\n" +
                    "local pass_count = 0\n" +
                    "for i = 1, key_count do\n" +
                    "    local window_key = KEYS[i]\n" +
                    "    local limit = tonumber(ARGV[i * 2 - 1])\n" +
                    "    local window = tonumber(ARGV[i * 2])\n" +
                    "    local window_start = current_time - window\n" +
                    "    redis.call('ZREMRANGEBYSCORE', window_key, '-inf', window_start)\n" +
                    "    local current_count = redis.call('ZCARD', window_key)\n" +
                    "    if current_count < limit then\n" +
                    "        pass_count = pass_count + 1\n" +
                    "    end\n" +
                    "end\n" +
                    "if pass_count == key_count then\n" +
                    "    for i = 1, key_count do\n" +
                    "        local window_key = KEYS[i]\n" +
                    "        local window = tonumber(ARGV[i * 2])\n" +
                    "        local member = ARGV[key_count * 2 + i]\n" +
                    "        redis.call('ZADD', window_key, current_time, member)\n" +
                    "        redis.call('EXPIRE', window_key, math.ceil(window / 1000000000) + 1)\n" + // 1000000000 = NANOSECONDS_PER_SECOND
                    "    end\n" +
                    "    return 1\n" +
                    "end\n" +
                    "return 0";

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
            Thread thread = new Thread(r, "SmartRedisLimiter-SlidingWindow-Timer");
            thread.setDaemon(true);
            return thread;
        });

        log.info("SmartRedisLimiter 滑动窗口初始化完成, 集群模式: {}, 超时控制: {}ms",
                isClusterMode,
                properties.getRedis().getCommandTimeout());
    }

    @Override
    public String getAlgorithm() {
        return SmartRedisLimiterConstant.ALGORITHM_SLIDING;
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
            log.info("SmartRedisLimiter 滑动窗口计时器已关闭");
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
            log.warn("SmartRedisLimiter 滑动窗口 Redis操作超时({}ms)，触发降级策略", timeout);
            return handleFallback(e, fallbackStrategy);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("SmartRedisLimiter 滑动窗口执行异常", cause != null ? cause : e);
            return handleFallback(cause != null ? (Exception) cause : e, fallbackStrategy);

        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            log.error("SmartRedisLimiter 滑动窗口执行被中断", e);
            return handleFallback(e, fallbackStrategy);

        } catch (Exception e) {
            log.error("SmartRedisLimiter 滑动窗口执行异常", e);
            return handleFallback(e, fallbackStrategy);
        }
    }

    /**
     * 实际执行Redis滑动窗口限流检查
     */
    private boolean executeRedisLimit(SmartRedisLimiterContext context,
                                      List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                      String keyStrategy) {
        String baseKey = buildBaseKey(context, keyStrategy);

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        // 当前时间戳（纳秒）- 用于 ZSET score 和 current_time 统一单位
        long currentTimeNano = System.nanoTime();

        for (SmartRedisLimiterProperties.SmartLimitRule rule : limitRules) {
            String windowKey = buildWindowKey(baseKey, rule.getWindowSeconds());
            keys.add(windowKey);
            // limit
            args.add(String.valueOf(rule.getCount()));
            // window (纳秒)
            args.add(String.valueOf(rule.getWindowSeconds() * SmartRedisLimiterRedisKeyConstant.NANOSECONDS_PER_SECOND));
        }

        // 添加 member: 纯字符串，非数字，避免 Lua tonumber() 误解析
        String member = "m-" + Thread.currentThread().getId() + "-" + currentTimeNano;
        args.add(member);

        // currentTime 放在末尾（纳秒）
        args.add(String.valueOf(currentTimeNano));

        Long result = redisTemplate.execute(limiterScript, keys, args.toArray(new Object[0]));

        boolean passed = result != null && result == 1L;

        if (!passed) {
            log.warn("SmartRedisLimiter 滑动窗口限流触发: key={}, rules={}", baseKey, limitRules);
        } else {
            log.debug("SmartRedisLimiter 滑动窗口限流通过: key={}", baseKey);
        }

        return passed;
    }

    /**
     * 构建窗口Key
     */
    String buildWindowKey(String baseKey, long windowSeconds) {
        String windowSuffix = SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR +
                windowSeconds +
                SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW;

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
