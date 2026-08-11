package io.github.surezzzzzz.sdk.ops.middleware.kafka;

/**
 * Kafka Route 只读运维视图适配口。
 *
 * @author surezzzzzz
 */
public interface KafkaOperationsViewAdapter {

    /**
     * 获取 Kafka 数据源诊断清单。
     *
     * @return 诊断清单
     */
    KafkaDatasourceListResponse listDatasources();

    /**
     * 获取 topic 分页清单。
     *
     * @param request 查询请求
     * @return topic 清单
     */
    KafkaTopicListResponse listTopics(KafkaTopicListRequest request);

    /**
     * 获取消费组分页清单。
     *
     * @param request 查询请求
     * @return 消费组清单
     */
    KafkaConsumerGroupListResponse listConsumerGroups(KafkaConsumerGroupListRequest request);

    /**
     * 获取手工输入 Topic 的分区状态。
     *
     * @param request 查询请求
     * @return Topic 分区状态
     */
    KafkaTopicRuntimeResponse getTopicRuntime(KafkaTopicRuntimeRequest request);

    /**
     * 获取手工输入消费组的分区积压。
     *
     * @param request 查询请求
     * @return 消费组积压分页结果
     */
    KafkaConsumerGroupLagListResponse getConsumerGroupLag(KafkaConsumerGroupLagListRequest request);
}
