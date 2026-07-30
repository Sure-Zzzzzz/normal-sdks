package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

/**
 * 错误处理结果
 *
 * @author surezzzzzz
 */
public enum ErrorHandlerOutcome {

    /**
     * 重试：框架按退避时长等待后重新执行 handler，不推进 offset
     */
    RETRY,

    /**
     * 死信：框架投递死信后推进 offset
     */
    DEAD_LETTER
}
