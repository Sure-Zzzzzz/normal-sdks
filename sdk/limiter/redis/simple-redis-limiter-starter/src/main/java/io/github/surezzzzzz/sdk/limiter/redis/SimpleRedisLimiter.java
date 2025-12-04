package io.github.surezzzzzz.sdk.limiter.redis;

import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.constant.TokenResult;
import io.github.surezzzzzz.sdk.limiter.redis.exception.InitException;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/12/11 11:23
 */
@RedisLimiterComponent
@Slf4j
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis", name = "enable", havingValue = "true")
public class SimpleRedisLimiter {
    @Autowired
    @Qualifier("simpleRedisLimiterRedisTemplate")
    protected RedisTemplate<String, String> simpleRedisLimiterRedisTemplate;
    @Autowired
    private RedisLimiterProperties redisLimiterProperties;
    @Autowired
    private SimpleRedisLock simpleRedisLock;
    @Autowired
    private TaskRetryExecutor taskRetryExecutor;
    @Getter
    private String initLockKey;
    @Getter
    private String initializedKey;
    @Getter
    private String resetSetLockKey;
    @Getter
    private String resetTokenLockKey;
    @Getter
    private String tokenBucket;
    @Getter
    private String setBucket;
    @Getter
    private String compressSetBucket;

    private final RedisScript<Long> storeScript = new DefaultRedisScript<>(
            "local setKey = KEYS[1]\n" +
                    "local tokenKey = KEYS[2]\n" +
                    "local something = ARGV[1]\n" +
                    "if redis.call('SISMEMBER', setKey, something) == 1 then\n" +
                    "    return 2\n" +
                    "end\n" +
                    "local remainingTokens = redis.call('DECR', tokenKey)\n" +
                    "if remainingTokens < 0 then\n" +
                    "    redis.call('INCR', tokenKey)\n" +
                    "    return 0\n" +
                    "end\n" +
                    "redis.call('SADD', setKey, something)\n" +
                    "return 1",
            Long.class);
    private final RedisScript<Long> noStoreScript = new DefaultRedisScript<>(
            "local tokenKey = KEYS[1]\n" +
                    "local remainingTokens = redis.call('DECR', tokenKey)\n" +
                    "if remainingTokens < 0 then\n" +
                    "    redis.call('INCR', tokenKey)\n" +
                    "    return 0\n" +
                    "end\n" +
                    "return 1",
            Long.class);

    @PostConstruct
    private void init() {
        log.debug("SimpleRedisLimiter初始化");
        initLockKey = String.format("%s:%s", redisLimiterProperties.getLockKey(), redisLimiterProperties.getMe());
        initializedKey = String.format("%s:%s", redisLimiterProperties.getInitializedKey(), redisLimiterProperties.getMe());
        resetSetLockKey = String.format("%s:%s:scheduled:set", redisLimiterProperties.getLockKey(), redisLimiterProperties.getMe());
        resetTokenLockKey = String.format("%s:%s:scheduled:token", redisLimiterProperties.getLockKey(), redisLimiterProperties.getMe());
        tokenBucket = String.format("%s:%s", redisLimiterProperties.getToken().getBucket(), redisLimiterProperties.getMe());
        setBucket = String.format("%s:%s", redisLimiterProperties.getSet().getBucket(), redisLimiterProperties.getMe());
        compressSetBucket = String.format("%s:%s", redisLimiterProperties.getSet().getCompressBucket(), redisLimiterProperties.getMe());
        log.info("Redis限流器Key配置 - initLockKey: {}, initializedKey: {}, resetSetLockKey: {}, resetTokenLockKey: {}, tokenBucket: {}, setBucket: {}, compressSetBucket: {}",
                initLockKey, initializedKey, resetSetLockKey, resetTokenLockKey, tokenBucket, setBucket, compressSetBucket);
        try {
            // 使用 TaskRetryExecutor 执行带指数退避的重试任务
            taskRetryExecutor.executeWithRetry(() -> {
                // 尝试获取分布式锁，只有一个实例能获取到锁
                if (simpleRedisLock.tryLock(
                        initLockKey,
                        redisLimiterProperties.getLockValue(),
                        redisLimiterProperties.getLockExpiryTime(),
                        TimeUnit.SECONDS
                )) {
                    // 判断是否已经初始化过存储桶
                    Boolean isInitialized = simpleRedisLimiterRedisTemplate.hasKey(initializedKey);
                    if (isInitialized == null || !isInitialized) {
                        // 如果没有初始化过，则进行初始化
                        log.info("容器启动，进行存储桶初始化...");
                        initializeBuckets();  // 调用初始化方法
                    } else {
                        log.info("存储桶已初始化，跳过初始化");
                    }
                } else {
                    log.info("其他实例已获取锁，跳过初始化");
                }
                return null;  // 成功时返回null
            }, redisLimiterProperties.getMaxRetries(), redisLimiterProperties.getRetryInterval());
        } catch (Exception e) {
            log.error("存储桶初始化失败: {}", e.getMessage(), e);
        }
    }

    public void initializeBuckets() {
        try {
            // 使用 TaskRetryExecutor 执行带指数退避的重试任务
            taskRetryExecutor.executeWithRetry(() -> {
                // 初始化Token桶和Set桶
                initTokenBucket();
                initSetBucket();
                // 初始化完成后设置标记
                simpleRedisLimiterRedisTemplate.opsForValue().set(initializedKey, "true");
                log.info("存储桶初始化完成");
                return null;  // 成功时返回null
            }, redisLimiterProperties.getMaxRetries(), redisLimiterProperties.getRetryInterval());
        } catch (Exception e) {
            log.error("存储桶初始化失败: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "#{redisLimiterProperties.getSet().getCron()}")
    public void resetSet() {
        // 提前获取配置，判断是否需要重置
        boolean shouldResetSet = redisLimiterProperties.getSet().isReset();
        if (!shouldResetSet) {
            log.info("Set 桶无需重置，跳过任务");
            return;
        }

        try {
            // 使用 TaskRetryExecutor 执行带指数退避的重试任务
            taskRetryExecutor.executeWithRetry(() -> {
                // 获取 Set 桶定时任务的锁
                if (simpleRedisLock.tryLock(
                        resetSetLockKey,
                        redisLimiterProperties.getLockValue(),
                        redisLimiterProperties.getScheduledLockExpiryTime(),
                        TimeUnit.SECONDS
                )) {
                    log.info("重置 Set 桶");
                    initSetBucket(); // 调用方法进行初始化
                } else {
                    log.info("其他实例已获取Set 桶定时任务锁，跳过Set 桶重置任务");
                }
                return null;  // 成功时返回null
            }, redisLimiterProperties.getMaxRetries(), redisLimiterProperties.getRetryInterval());
        } catch (Exception e) {
            log.error("Set 桶重置失败: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "#{redisLimiterProperties.getToken().getCron()}")
    public void resetToken() {
        // 提前获取配置，判断是否需要重置
        boolean shouldResetToken = redisLimiterProperties.getToken().isReset();
        if (!shouldResetToken) {
            log.info("Token 桶无需重置，跳过任务");
            return;
        }

        try {
            // 使用 TaskRetryExecutor 执行带指数退避的重试任务
            taskRetryExecutor.executeWithRetry(() -> {
                // 获取 Token 桶定时任务的锁
                if (simpleRedisLock.tryLock(
                        resetTokenLockKey,
                        redisLimiterProperties.getLockValue(),
                        redisLimiterProperties.getScheduledLockExpiryTime(),
                        TimeUnit.SECONDS
                )) {
                    log.info("重置 Token 桶");
                    initTokenBucket(); // 调用方法进行初始化
                } else {
                    log.info("其他实例已获取Token 桶定时任务锁，跳过Token 桶重置任务");
                }
                return null;  // 成功时返回null
            }, redisLimiterProperties.getMaxRetries(), redisLimiterProperties.getRetryInterval());
        } catch (Exception e) {
            log.error("Token 桶重置失败: {}", e.getMessage(), e);
        }
    }

    private void initTokenBucket() {
        if (!redisLimiterProperties.getToken().isEnable()) {
            log.debug("Token桶未启用");
            return;
        }
        try {
            // 使用 TaskRetryExecutor 执行带指数退避的重试任务
            taskRetryExecutor.executeWithRetry(() -> {
                try {
                    // 删除旧的 Token 桶
                    simpleRedisLimiterRedisTemplate.delete(tokenBucket);
                    // 初始化新的 Token 桶
                    simpleRedisLimiterRedisTemplate.opsForValue().set(tokenBucket,
                            String.valueOf(redisLimiterProperties.getToken().getSize()).replace("\u0000", ""),
                            calculateExpireTimeInSeconds(redisLimiterProperties.getToken().getCron()),
                            TimeUnit.SECONDS);
                    log.info("Token 桶初始化完成: {}", tokenBucket);
                } catch (Exception e) {
                    log.error("Token 桶初始化失败: {}", e.getMessage(), e);
                    throw e;  // 抛出异常，以便重试机制处理
                }
                return null;  // 成功时返回null
            }, redisLimiterProperties.getMaxRetries(), redisLimiterProperties.getRetryInterval());
        } catch (Exception e) {
            log.error("Token 桶初始化失败: {}", e.getMessage(), e);
        }
    }

    private void initSetBucket() {
        if (!redisLimiterProperties.getSet().isEnable()) {
            log.debug("Set桶未启用");
            return;
        }
        try {
            // 使用 TaskRetryExecutor 执行带指数退避的重试任务
            taskRetryExecutor.executeWithRetry(() -> {
                try {
                    // 删除旧的 Set 桶
                    simpleRedisLimiterRedisTemplate.delete(setBucket);
                    // 删除压缩桶
                    simpleRedisLimiterRedisTemplate.delete(compressSetBucket);

                    // 子类可以设置初始值
                    setSetInitialValue(setBucket);
                    setCompressSetInitialValue(compressSetBucket);
                    log.info("Set 桶初始化完成: {}", setBucket);
                } catch (Exception e) {
                    log.error("Set 桶初始化失败: {}", e.getMessage(), e);
                    throw e;  // 抛出异常，以便重试机制处理
                }
                return null;  // 成功时返回null
            }, redisLimiterProperties.getMaxRetries(), redisLimiterProperties.getRetryInterval());
        } catch (Exception e) {
            log.error("Set 桶初始化失败: {}", e.getMessage(), e);
        }
    }

    // 设置 Set 桶的初始值，留给子类实现
    public void setSetInitialValue(String setBucket) {
        // 默认的行为是为空，子类可以重写该方法，设置具体的初始值
        log.info("Set 桶{} 初始值设置为默认", setBucket);
    }

    // 设置 Set 桶的初始值，留给子类实现
    public void setCompressSetInitialValue(String compressSetBucket) {
        // 默认的行为是为空，子类可以重写该方法，设置具体的初始值
        log.info("Set 桶{} 初始值设置为默认", compressSetBucket);
    }

    /**
     * 获取token
     *
     * @return 是否获取成功
     */
    public boolean getToken() {
        if (!redisLimiterProperties.getToken().isEnable()) {
            log.debug("Token桶未启用");
            throw new InitException("Token桶未启用");
        }
        // 执行仅令牌校验的脚本
        List<String> keys = Collections.singletonList(tokenBucket);
        Long result = simpleRedisLimiterRedisTemplate.execute(
                noStoreScript,
                keys
        );
        return result != null && result == 1;
    }

    /**
     * 为某个字符串获取token，并将这个字符串放到set里，value不过期，但遵守桶reset的规则
     *
     * @param something
     * @return
     */
    public int getToken(String something) {
        return getToken(something, false);
    }

    /**
     * @param something
     * @param hash
     * @return
     */
    public int getToken(String something, boolean hash) {
        if (!redisLimiterProperties.getToken().isEnable()) {
            log.debug("Token桶未启用");
            return TokenResult.UNKNOWN.getCode();
        }
        if (!redisLimiterProperties.getSet().isEnable()) {
            log.debug("Set桶未启用");
            return TokenResult.UNKNOWN.getCode();
        }

        log.debug("执行 getToken | something: {}, hash: {}", something, hash);
        List<String> keys = Arrays.asList(hash ? compressSetBucket : setBucket, tokenBucket);

        Long result = simpleRedisLimiterRedisTemplate.execute(
                storeScript,
                keys,
                hash ? String.valueOf(something.hashCode()) : something
        );

        log.debug("执行 getToken | result: {}", result);
        return TokenResult.fromCode(result).getCode();
    }

    public long calculateExpireTimeInSeconds(String cron) {
        try {
            // 解析 Cron 表达式
            CronExpression cronExpression = CronExpression.parse(cron);
            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            // 获取下一个有效时间
            LocalDateTime nextValidTime = cronExpression.next(now);
            if (nextValidTime != null) {
                // 计算当前时间到下次触发时间的间隔
                Duration duration = Duration.between(now, nextValidTime);
                return duration.getSeconds(); // 转换为秒
            }
        } catch (Exception e) {
            log.error("计算Cron表达式{}的过期时间失败", cron, e);
        }
        return -1; // 如果计算失败，则默认返回 -1
    }
}
