package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail;

import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.KafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka 消费组安全详情执行器。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerGroupDetailExecutor
        extends AbstractMiddlewareOpsExecutor<KafkaConsumerGroupDetailRequest, KafkaConsumerGroupDetailResponse> {

    private final KafkaOperationsViewAdapter adapter;

    public KafkaConsumerGroupDetailExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaConsumerGroupDetailRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaConsumerGroupDetailResponse execute(KafkaConsumerGroupDetailRequest request) {
        return adapter.getConsumerGroupDetail(request);
    }
}
