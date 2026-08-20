package io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime;

import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.KafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka Topic 分区状态执行器。
 *
 * @author surezzzzzz
 */
public class KafkaTopicRuntimeExecutor extends AbstractMiddlewareOpsExecutor<KafkaTopicRuntimeRequest, KafkaTopicRuntimeResponse> {

    private final KafkaOperationsViewAdapter adapter;

    /**
     * 创建 Kafka Topic 分区状态执行器。
     *
     * @param adapter Kafka Route 安全适配器
     */
    public KafkaTopicRuntimeExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaTopicRuntimeRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaTopicRuntimeResponse execute(KafkaTopicRuntimeRequest request) {
        return adapter.getTopicRuntime(request);
    }
}
