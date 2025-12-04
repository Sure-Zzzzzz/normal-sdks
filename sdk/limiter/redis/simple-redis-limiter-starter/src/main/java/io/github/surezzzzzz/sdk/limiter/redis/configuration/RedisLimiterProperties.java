package io.github.surezzzzzz.sdk.limiter.redis.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.UUID;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/17 11:24
 */
@Getter
@Setter
@NoArgsConstructor
@RedisLimiterComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.limiter.redis")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis", name = "enable", havingValue = "true")
@Slf4j
public class RedisLimiterProperties {
    @PostConstruct
    public void init() {
        log.debug("RedisLimiterProperties enable:{}", enable);
    }

    private boolean enable = false;
    private String me = "default";// 用来区别不同的应用
    private String initializedKey = "surezzzzzz_redis_limiter_initialized";// 初始化标志的 Redis Key
    private String lockKey = "surezzzzzz_redis_limiter_lock";// 分布式锁的 Redis Key
    private String lockValue = UUID.randomUUID().toString();// 分布式锁的 Redis Value
    private long lockExpiryTime = 300L; // 锁的过期时间，单位秒
    private long scheduledLockExpiryTime = 300L; // 定时任务的锁过期时间，单位秒（根据定时任务时长设置）
    private int maxRetries = 20;  // 最大重试次数
    private int retryInterval = 30000;  // 重试间隔，单位：毫秒 (30秒)

    private Token token = new Token();
    private Set set = new Set();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Set {
        private boolean reset = true;
        private boolean enable = true;
        private String cron = "0 0 0 * * ?";
        private String bucket = "surezzzzzz_redis_limiter_set_bucket";
        private String compressBucket = "surezzzzzz_redis_limiter_compress_set_bucket";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Token {
        private boolean reset = true;
        private boolean enable = true;
        private String cron = "0 0 0 * * ?";
        private long size = 800000000;
        private String bucket = "surezzzzzz_redis_limiter_token_bucket";
    }
}