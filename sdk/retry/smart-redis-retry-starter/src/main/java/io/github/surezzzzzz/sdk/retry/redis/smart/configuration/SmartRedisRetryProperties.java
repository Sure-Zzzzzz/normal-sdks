package io.github.surezzzzzz.sdk.retry.redis.smart.configuration;

import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Smart Redis Retry 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SmartRedisRetryConstant.CONFIG_PREFIX)
public class SmartRedisRetryProperties {

    /**
     * 是否启用
     */
    private boolean enable = SmartRedisRetryConstant.DEFAULT_ENABLE;
    /**
     * Redis 配置
     */
    private RedisConfig redis = new RedisConfig();
    /**
     * 防护配置
     */
    private GuardConfig guard = new GuardConfig();
    /**
     * 重试策略配置
     */
    private PolicyConfig policy = new PolicyConfig();

    /**
     * Redis 配置
     */
    @Data
    public static class RedisConfig {
        /**
         * Redis Key 前缀
         */
        private String keyPrefix = SmartRedisRetryConstant.DEFAULT_KEY_PREFIX;
        /**
         * 实例标识
         */
        private String me = SmartRedisRetryConstant.DEFAULT_ME;
        /**
         * 是否为记录标识使用 Redis Cluster hash tag
         */
        private boolean useHashTag = SmartRedisRetryConstant.DEFAULT_USE_HASH_TAG;
        /**
         * 单次扫描建议返回的记录数
         */
        private int scanCount = SmartRedisRetryConstant.DEFAULT_SCAN_COUNT;
        /**
         * 重试记录存活时间，单位秒
         */
        private long recordTtlSeconds = SmartRedisRetryConstant.DEFAULT_RECORD_TTL_SECONDS;
        /**
         * 重试耗尽后是否保留记录
         */
        private boolean retainExhausted = SmartRedisRetryConstant.DEFAULT_RETAIN_EXHAUSTED;
    }

    /**
     * 防护配置
     */
    @Data
    public static class GuardConfig {
        /**
         * 重试标识最大长度
         */
        private int maxRetryKeyLength = SmartRedisRetryConstant.DEFAULT_MAX_RETRY_KEY_LENGTH;
        /**
         * 上下文 JSON 最大长度
         */
        private int maxContextJsonLength = SmartRedisRetryConstant.DEFAULT_MAX_CONTEXT_JSON_LENGTH;
        /**
         * Redis 操作失败策略
         */
        private String redisFailureStrategy = SmartRedisRetryConstant.DEFAULT_REDIS_FAILURE_STRATEGY;
    }

    /**
     * 重试策略配置
     */
    @Data
    public static class PolicyConfig {
        /**
         * 默认重试策略
         */
        private RetryPolicy defaultPolicy = RetryPolicy.defaultPolicy();
        /**
         * 按重试类型配置的场景策略
         */
        private Map<String, RetryPolicy> scene = new HashMap<String, RetryPolicy>();
    }
}
