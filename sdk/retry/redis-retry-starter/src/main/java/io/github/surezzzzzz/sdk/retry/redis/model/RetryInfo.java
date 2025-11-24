package io.github.surezzzzzz.sdk.retry.redis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 重试信息模型
 *
 * @author: Sure.
 * @Date: 2025/3/11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryInfo {

    /**
     * 当前重试次数
     */
    private Integer count;

    /**
     * ✅ 最大重试次数
     */
    private Integer maxRetryTimes;

    /**
     * ✅ 重试间隔（毫秒）
     */
    private Long retryIntervalMs;

    /**
     * 首次失败时间
     */
    private Long firstFailTime;

    /**
     * 最后一次失败时间
     */
    private Long lastFailTime;

    /**
     * ✅ 下次可以重试的时间
     */
    private Long nextRetryTime;

    /**
     * 最后一次错误信息
     */
    private String lastError;

    /**
     * ✅ 上下文信息（存储 job、record 等）
     */
    private Map<String, Object> context;
}
