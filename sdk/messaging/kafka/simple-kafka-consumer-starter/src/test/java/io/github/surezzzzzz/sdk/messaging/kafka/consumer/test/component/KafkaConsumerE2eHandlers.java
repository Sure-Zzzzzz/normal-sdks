package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.component;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumerComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.KafkaConsumerE2eRecorder;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.KafkaConsumerEndToEndHelper;

import java.util.concurrent.TimeoutException;

/**
 * Kafka Consumer 端到端测试消费入口。
 *
 * @author surezzzzzz
 */
@SimpleKafkaConsumerComponent
public class KafkaConsumerE2eHandlers {

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_V110,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V110, groupId = "mock-consumer-v110-e2e")
    public void consumeV110(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_V28,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V28, groupId = "mock-consumer-v28-e2e")
    public void consumeV28(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_V37,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-v37-e2e")
    public void consumeV37(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_CLUSTER,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_CLUSTER, groupId = "mock-consumer-cluster-e2e")
    public void consumeCluster(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_IDEMPOTENCY,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-idempotency-e2e")
    public void consumeIdempotency(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_RETRY,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-retry-e2e")
    public void consumeRetry(KafkaConsumerRecord<String, String> record) throws Exception {
        KafkaConsumerE2eRecorder.record(record);
        if ("retryable".equals(record.getValue())) {
            throw new TimeoutException("mock retryable failure");
        }
        if ("fatal".equals(record.getValue())) {
            throw new IllegalArgumentException("mock fatal failure");
        }
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_AUTO_COMMIT,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-auto-commit-e2e")
    public void consumeAutoCommit(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_REFRESH,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-refresh-e2e")
    public void consumeRefresh(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_SAME_TOPIC_GROUP,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-group-a-e2e")
    public void consumeSameTopicGroupA(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_SAME_TOPIC_GROUP,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId = "mock-consumer-group-b-e2e")
    public void consumeSameTopicGroupB(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_DEAD_LETTER_RECOVERY,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37,
            groupId = "mock-consumer-dead-letter-recovery-e2e")
    public void consumeDeadLetterRecovery(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
        throw new IllegalArgumentException("mock dead letter recovery failure");
    }

    @SimpleKafkaConsumer(topic = KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE,
            datasource = KafkaConsumerEndToEndHelper.DATASOURCE_V37,
            groupId = "mock-consumer-processing-lease-e2e")
    public void consumeProcessingLease(KafkaConsumerRecord<String, String> record) {
        KafkaConsumerE2eRecorder.record(record);
    }
}
