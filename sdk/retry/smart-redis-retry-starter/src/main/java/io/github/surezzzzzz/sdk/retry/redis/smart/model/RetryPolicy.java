package io.github.surezzzzzz.sdk.retry.redis.smart.model;

import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 重试策略
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 最大重试次数
     */
    private Integer maxRetryTimes;
    /**
     * 初始重试间隔，单位毫秒
     */
    private Long retryIntervalMillis;
    /**
     * 最大重试间隔，单位毫秒
     */
    private Long maxIntervalMillis;
    /**
     * 退避倍数
     */
    private Double backoffMultiplier;
    /**
     * 抖动比例
     */
    private Double jitterRatio;

    /**
     * 创建默认重试策略
     *
     * @return 默认重试策略
     */
    public static RetryPolicy defaultPolicy() {
        return RetryPolicy.builder()
                .maxRetryTimes(SmartRedisRetryConstant.DEFAULT_MAX_RETRY_TIMES)
                .retryIntervalMillis(SmartRedisRetryConstant.DEFAULT_RETRY_INTERVAL_MILLIS)
                .maxIntervalMillis(SmartRedisRetryConstant.DEFAULT_MAX_INTERVAL_MILLIS)
                .backoffMultiplier(SmartRedisRetryConstant.DEFAULT_BACKOFF_MULTIPLIER)
                .jitterRatio(SmartRedisRetryConstant.DEFAULT_JITTER_RATIO)
                .build();
    }
}
