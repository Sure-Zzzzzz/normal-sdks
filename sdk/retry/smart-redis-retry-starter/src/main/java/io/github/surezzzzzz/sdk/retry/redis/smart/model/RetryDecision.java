package io.github.surezzzzzz.sdk.retry.redis.smart.model;

import io.github.surezzzzzz.sdk.retry.redis.smart.constant.RetryDecisionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 重试决策
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 决策类型
     */
    private RetryDecisionType type;
    /**
     * 是否允许重试
     */
    private boolean allowed;
    /**
     * 当前失败次数
     */
    private Integer currentCount;
    /**
     * 最大重试次数
     */
    private Integer maxRetryTimes;
    /**
     * 下次允许重试时间，毫秒时间戳
     */
    private Long nextRetryTime;
    /**
     * 还需等待时间，单位毫秒
     */
    private Long waitMillis;
    /**
     * 关联的重试状态
     */
    private RetryInfo retryInfo;
}
