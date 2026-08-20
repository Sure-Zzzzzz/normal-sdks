package io.github.surezzzzzz.sdk.ops.middleware.kafka.datasource;

import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.KafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Kafka 数据源诊断清单执行器。
 *
 * @author surezzzzzz
 */
public class KafkaDatasourceListExecutor
        extends AbstractMiddlewareOpsExecutor<KafkaDatasourceListRequest, KafkaDatasourceListResponse> {

    private final KafkaOperationsViewAdapter adapter;

    /**
     * 创建执行器。
     *
     * @param adapter Kafka Route 安全适配器
     */
    public KafkaDatasourceListExecutor(KafkaOperationsViewAdapter adapter) {
        super(KafkaDatasourceListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public KafkaDatasourceListResponse execute(KafkaDatasourceListRequest request) {
        return adapter.listDatasources();
    }
}
