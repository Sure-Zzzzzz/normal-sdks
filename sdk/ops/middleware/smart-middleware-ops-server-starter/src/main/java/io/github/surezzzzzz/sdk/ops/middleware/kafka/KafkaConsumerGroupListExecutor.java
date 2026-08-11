package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka 消费组清单执行器。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerGroupListExecutor
        extends AbstractMiddlewareOpsExecutor<KafkaConsumerGroupListRequest, KafkaConsumerGroupListResponse> {

    private final KafkaOperationsViewAdapter adapter;

    /**
     * 创建执行器。
     *
     * @param adapter Kafka Route 安全适配器
     */
    public KafkaConsumerGroupListExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaConsumerGroupListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaConsumerGroupListResponse execute(KafkaConsumerGroupListRequest request) {
        return adapter.listConsumerGroups(request);
    }
}
