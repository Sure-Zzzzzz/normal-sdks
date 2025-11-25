package io.github.surezzzzzz.sdk.retry.redis.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;

/**
 * Redis 重试配置属性
 *
 * @author: Sure.
 * @Date: 2025/3/11
 */
@Data
@Validated
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.retry.redis")
public class RedisRetryProperties {

    /**
     * 最大重试次数
     */
    @Min(value = 1, message = "最大重试次数不能小于1")
    private int maxRetryCount = 3;

    /**
     * 重试记录TTL（秒）
     */
    @Min(value = 60, message = "重试记录TTL不能小于60秒")
    private int retryRecordTtlSeconds = 24 * 60 * 60; // 24小时

    /**
     * 基础延迟时间（毫秒）
     */
    @Min(value = 100, message = "基础延迟时间不能小于100毫秒")
    private int baseDelayMs = 1000;

    /**
     * 最大延迟时间（毫秒）
     */
    @Min(value = 1000, message = "最大延迟时间不能小于1000毫秒")
    private int maxDelayMs = 30000;

    /**
     * 强制使用hash tag策略
     * null: 自动检测Redis环境类型
     * true: 强制使用hash tag（适用于Redis Cluster）
     * false: 强制不使用hash tag（适用于单机Redis）
     */
    private Boolean forceHashTag = null;

    /**
     * 验证配置合理性
     */
    public void validate() {
        if (baseDelayMs >= maxDelayMs) {
            throw new IllegalArgumentException("基础延迟时间必须小于最大延迟时间");
        }
    }

    @Override
    public String toString() {
        return String.format("RedisRetryProperties{" +
                        "maxRetryCount=%d, retryRecordTtlSeconds=%d, " +
                        "baseDelayMs=%d, maxDelayMs=%d, forceHashTag=%s" +
                        "}",
                maxRetryCount, retryRecordTtlSeconds,
                baseDelayMs, maxDelayMs, forceHashTag);
    }
}