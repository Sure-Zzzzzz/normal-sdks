package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author: Sure.
 * @description 智能Redis限流执行器（带超时控制）
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterExecutor {

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
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

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

        // ✅ 单线程计时器
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SmartRedisLimiter-Timer");
            thread.setDaemon(true);
            return thread;
        });

        log.info("SmartRedisLimiter 初始化完成, 集群模式: {}, 超时控制: {}ms",
                isClusterMode,
                properties.getRedis().getCommandTimeout());
    }

    @PreDestroy
    public void destroy() {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdown();
            log.info("SmartRedisLimiter 计时器已关闭");
        }
    }

    /**
     * 检测Redis是否为集群模式
     */
    private boolean detectClusterMode() {
        try {
            RedisConnectionFactory connectionFactory = smartRedisLimiterRedisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return false;
            }

            try (RedisClusterConnection clusterConnection = connectionFactory.getClusterConnection()) {
                return clusterConnection != null;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            log.debug("检测Redis集群模式失败，默认为单机模式", e);
            return false;
        }
    }

    /**
     * 执行限流检查（带超时控制）
     */
    public boolean tryAcquire(SmartRedisLimiterContext context,
                              List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                              String keyStrategy) {
        long timeout = properties.getRedis().getCommandTimeout();

        // ✅ 用FutureTask包装
        FutureTask<Boolean> task = new FutureTask<>(() ->
                executeRedisLimit(context, limitRules, keyStrategy)
        );

        // ✅ 提交给计时器执行
        timeoutScheduler.execute(task);

        try {
            // ✅ 等待结果，超时抛异常
            return task.get(timeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            task.cancel(true);  // 取消任务
            log.warn("SmartRedisLimiter Redis操作超时({}ms)，触发降级策略", timeout);
            return handleFallback(e);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("SmartRedisLimiter 执行异常", cause != null ? cause : e);
            return handleFallback(cause != null ? (Exception) cause : e);

        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            log.error("SmartRedisLimiter 执行被中断", e);
            return handleFallback(e);

        } catch (Exception e) {
            log.error("SmartRedisLimiter 执行异常", e);
            return handleFallback(e);
        }
    }

    /**
     * 实际执行Redis限流检查的逻辑
     */
    private boolean executeRedisLimit(SmartRedisLimiterContext context,
                                      List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                      String keyStrategy) {
        // 1. 生成Key
        SmartRedisLimiterKeyGenerator keyGenerator = getKeyGenerator(keyStrategy);
        String keyPart = keyGenerator.generate(context);
        String baseKey = SmartRedisLimiterRedisKeyConstant.KEY_PREFIX +
                properties.getMe() +
                SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR +
                keyPart;

        // 2. 构建多个时间窗口的Key
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (SmartRedisLimiterProperties.SmartLimitRule rule : limitRules) {
            String windowKey = buildWindowKey(baseKey, rule.getWindowSeconds());
            keys.add(windowKey);
            args.add(String.valueOf(rule.getCount()));
            args.add(String.valueOf(rule.getWindowSeconds()));
        }

        // 3. 执行Lua脚本
        Long result = smartRedisLimiterRedisTemplate.execute(limiterScript, keys, args.toArray());

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
    private String buildWindowKey(String baseKey, long windowSeconds) {
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

    /**
     * 获取Key生成器
     */
    private SmartRedisLimiterKeyGenerator getKeyGenerator(String strategyCode) {
        if (strategyCode == null || strategyCode.isEmpty()) {
            strategyCode = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        }

        String beanName = SmartRedisLimiterKeyStrategy.getBeanName(strategyCode);

        try {
            return applicationContext.getBean(beanName, SmartRedisLimiterKeyGenerator.class);
        } catch (Exception e) {
            log.error("SmartRedisLimiter 无法获取KeyGenerator: {}", beanName, e);
            throw new IllegalArgumentException("未找到KeyGenerator: " + beanName);
        }
    }

    /**
     * 降级处理
     */
    private boolean handleFallback(Exception e) {
        SmartRedisLimiterFallbackStrategy strategy =
                SmartRedisLimiterFallbackStrategy.fromCode(properties.getFallback().getOnRedisError());

        if (strategy == SmartRedisLimiterFallbackStrategy.DENY) {
            log.warn("SmartRedisLimiter Redis异常，降级策略: {} - 拒绝请求", strategy.getDesc());
            return false;
        } else {
            log.warn("SmartRedisLimiter Redis异常，降级策略: {} - 放行请求", strategy.getDesc());
            return true;
        }
    }
}
