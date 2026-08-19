package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka Topic 固定配置执行器。
 *
 * @author surezzzzzz
 */
public class KafkaTopicConfigExecutor extends AbstractMiddlewareOpsExecutor<KafkaTopicConfigRequest, KafkaTopicConfigResponse> {

    private final KafkaOperationsViewAdapter adapter;

    public KafkaTopicConfigExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaTopicConfigRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaTopicConfigResponse execute(KafkaTopicConfigRequest request) {
        return adapter.getTopicConfig(request);
    }
}
