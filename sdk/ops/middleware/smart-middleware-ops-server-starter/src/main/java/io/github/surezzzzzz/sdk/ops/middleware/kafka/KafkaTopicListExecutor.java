package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka topic 清单执行器。
 *
 * @author surezzzzzz
 */
public class KafkaTopicListExecutor
        extends AbstractMiddlewareOpsExecutor<KafkaTopicListRequest, KafkaTopicListResponse> {

    private final KafkaOperationsViewAdapter adapter;

    /**
     * 创建执行器。
     *
     * @param adapter Kafka Route 安全适配器
     */
    public KafkaTopicListExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaTopicListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaTopicListResponse execute(KafkaTopicListRequest request) {
        return adapter.listTopics(request);
    }
}
