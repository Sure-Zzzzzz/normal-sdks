package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag;

import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.KafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka 消费组积压查询执行器。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerGroupLagListExecutor
        extends AbstractMiddlewareOpsExecutor<KafkaConsumerGroupLagListRequest, KafkaConsumerGroupLagListResponse> {

    private final KafkaOperationsViewAdapter adapter;

    /**
     * 创建 Kafka 消费组积压查询执行器。
     *
     * @param adapter Kafka Route 安全适配器
     */
    public KafkaConsumerGroupLagListExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaConsumerGroupLagListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaConsumerGroupLagListResponse execute(KafkaConsumerGroupLagListRequest request) {
        return adapter.getConsumerGroupLag(request);
    }
}
