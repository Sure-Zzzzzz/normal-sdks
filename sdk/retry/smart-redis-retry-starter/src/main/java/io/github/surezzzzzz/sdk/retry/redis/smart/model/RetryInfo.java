package io.github.surezzzzzz.sdk.retry.redis.smart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 重试状态信息
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前失败次数
     */
    private Integer count;
    /**
     * 最大重试次数
     */
    private Integer maxRetryTimes;
    /**
     * 当前基础重试间隔，单位毫秒
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
     * 首次失败时间，毫秒时间戳
     */
    private Long firstFailTime;
    /**
     * 最近失败时间，毫秒时间戳
     */
    private Long lastFailTime;
    /**
     * 下次允许重试时间，毫秒时间戳
     */
    private Long nextRetryTime;
    /**
     * 最近错误编码
     */
    private String lastErrorCode;
    /**
     * 最近错误消息
     */
    private String lastErrorMessage;
    /**
     * 重试上下文
     */
    private Map<String, Object> context;
}
