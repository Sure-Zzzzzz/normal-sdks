package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import lombok.Builder;
import lombok.Getter;

/**
 * 错误处理决策
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ErrorHandlerDecision {

    /**
     * 处理结果
     */
    private final ErrorHandlerOutcome outcome;

    /**
     * 退避时长（毫秒），仅 outcome 为 RETRY 时有效
     */
    private final long backoffMs;

    /**
     * 错误码，用于死信 header 与事件
     */
    private final String errorCode;

    /**
     * 是否可重试分类结果
     */
    private final boolean retryable;
}
