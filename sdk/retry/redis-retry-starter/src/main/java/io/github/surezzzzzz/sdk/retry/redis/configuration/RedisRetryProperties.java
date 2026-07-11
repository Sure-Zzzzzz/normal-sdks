package io.github.surezzzzzz.sdk.retry.redis.configuration;

import io.github.surezzzzzz.sdk.retry.redis.constant.RedisRetryConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 重试配置属性
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(RedisRetryConstant.CONFIG_PREFIX)
public class RedisRetryProperties {

    /**
     * 是否启用
     */
    private boolean enable = RedisRetryConstant.DEFAULT_ENABLE;

    /**
     * Redis Key 前缀
     */
    private String keyPrefix = RedisRetryConstant.DEFAULT_KEY_PREFIX;

    /**
     * 应用实例标识
     */
    private String me = RedisRetryConstant.DEFAULT_ME;

    /**
     * 最大重试次数
     */
    private int maxRetryCount = RedisRetryConstant.DEFAULT_MAX_RETRY_COUNT;

    /**
     * 重试记录 TTL，单位秒
     */
    private int retryRecordTtlSeconds = RedisRetryConstant.DEFAULT_RETRY_RECORD_TTL_SECONDS;

    /**
     * 基础延迟时间，单位毫秒
     */
    private int baseDelayMs = RedisRetryConstant.DEFAULT_BASE_DELAY_MS;

    /**
     * 最大延迟时间，单位毫秒
     */
    private int maxDelayMs = RedisRetryConstant.DEFAULT_MAX_DELAY_MS;

    /**
     * 强制使用 hash tag 策略，null 表示自动检测 Redis 环境
     */
    private Boolean forceHashTag = null;
}
