package io.github.surezzzzzz.sdk.kafka.route.template;

import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteInputType;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteOperationType;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteRecord;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.function.Function;

/**
 * Kafka route 显式路由门面
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class KafkaRouteTemplate {

    private final SimpleKafkaRouteRegistry registry;
    private final KafkaRouteResolver routeResolver;

    public KafkaTemplate<Object, Object> kafkaTemplate() {
        return registry.getKafkaTemplate();
    }

    public KafkaTemplate<Object, Object> kafkaTemplate(String datasourceKey) {
        return registry.getKafkaTemplate(datasourceKey);
    }

    public KafkaTemplate<Object, Object> kafkaTemplateByTopic(String topic) {
        validateTopic(topic);
        return registry.getKafkaTemplate(resolveTopicDatasource(topic, KafkaRouteOperationType.TEMPLATE));
    }

    public KafkaTemplate<Object, Object> kafkaTemplateByRouteKey(String routeKey) {
        validateRouteKey(routeKey);
        return registry.getKafkaTemplate(resolveRouteKeyDatasource(routeKey, null, KafkaRouteOperationType.TEMPLATE));
    }

    public <K, V> ListenableFuture<SendResult<K, V>> send(String topic, V value) {
        return send(topic, null, null, value);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> send(String topic, K key, V value) {
        return send(topic, null, key, value);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> send(String topic, Integer partition, K key, V value) {
        ProducerRecord<K, V> record = createRecord(topic, partition, null, key, value, null);
        return doSend(resolveTopicDatasource(topic, KafkaRouteOperationType.SEND), record);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> send(KafkaRouteRecord<K, V> record) {
        validateRouteRecord(record);
        ProducerRecord<K, V> producerRecord = createRecord(record.getTopic(), record.getPartition(),
                record.getTimestamp(), record.getKey(), record.getValue(), record.getHeaders());
        if (record.getRouteKey() == null) {
            return doSend(resolveTopicDatasource(record.getTopic(), KafkaRouteOperationType.SEND), producerRecord);
        }
        return doSend(resolveRouteKeyDatasource(record.getRouteKey(), record.getTopic(), KafkaRouteOperationType.SEND),
                producerRecord);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> send(ProducerRecord<K, V> record) {
        validateProducerRecord(record);
        return doSend(resolveTopicDatasource(record.topic(), KafkaRouteOperationType.SEND), record);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendByRouteKey(String routeKey, String topic, V value) {
        return sendByRouteKey(routeKey, topic, null, null, value);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendByRouteKey(String routeKey, String topic, K key, V value) {
        return sendByRouteKey(routeKey, topic, null, key, value);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendByRouteKey(String routeKey, String topic,
                                                                    Integer partition, K key, V value) {
        validateRouteKey(routeKey);
        ProducerRecord<K, V> record = createRecord(topic, partition, null, key, value, null);
        return doSend(resolveRouteKeyDatasource(routeKey, topic, KafkaRouteOperationType.SEND), record);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendByRouteKey(String routeKey, ProducerRecord<K, V> record) {
        validateRouteKey(routeKey);
        validateProducerRecord(record);
        return doSend(resolveRouteKeyDatasource(routeKey, record.topic(), KafkaRouteOperationType.SEND), record);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendOn(String datasourceKey, String topic, V value) {
        KafkaTemplate<Object, Object> template = registry.getKafkaTemplate(datasourceKey);
        ProducerRecord<K, V> record = createRecord(topic, null, null, null, value, null);
        return doSend(template, record);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendOn(String datasourceKey, String topic, K key, V value) {
        KafkaTemplate<Object, Object> template = registry.getKafkaTemplate(datasourceKey);
        ProducerRecord<K, V> record = createRecord(topic, null, null, key, value, null);
        return doSend(template, record);
    }

    public <K, V> ListenableFuture<SendResult<K, V>> sendOn(String datasourceKey, ProducerRecord<K, V> record) {
        KafkaTemplate<Object, Object> template = registry.getKafkaTemplate(datasourceKey);
        validateProducerRecord(record);
        return doSend(template, record);
    }

    public <T> T execute(String topic, Function<KafkaTemplate<Object, Object>, T> callback) {
        validateCallback(callback);
        validateTopic(topic);
        return callback.apply(registry.getKafkaTemplate(resolveTopicDatasource(topic, KafkaRouteOperationType.EXECUTE)));
    }

    public <T> T executeByRouteKey(String routeKey, Function<KafkaTemplate<Object, Object>, T> callback) {
        validateCallback(callback);
        validateRouteKey(routeKey);
        return callback.apply(registry.getKafkaTemplate(resolveRouteKeyDatasource(routeKey, null,
                KafkaRouteOperationType.EXECUTE)));
    }

    public <T> T executeOn(String datasourceKey, Function<KafkaTemplate<Object, Object>, T> callback) {
        KafkaTemplate<Object, Object> template = registry.getKafkaTemplate(datasourceKey);
        validateCallback(callback);
        return callback.apply(template);
    }

    private String resolveTopicDatasource(String topic, KafkaRouteOperationType operationType) {
        KafkaRouteContext context = KafkaRouteContext.builder()
                .topic(topic)
                .routeInput(topic)
                .inputType(KafkaRouteInputType.TOPIC)
                .operationType(operationType)
                .build();
        return routeResolver.resolveDataSource(context);
    }

    private String resolveRouteKeyDatasource(String routeKey, String topic, KafkaRouteOperationType operationType) {
        KafkaRouteContext context = KafkaRouteContext.builder()
                .topic(topic)
                .routeKey(routeKey)
                .routeInput(routeKey)
                .inputType(KafkaRouteInputType.ROUTE_KEY)
                .operationType(operationType)
                .build();
        return routeResolver.resolveDataSource(context);
    }

    private <K, V> ProducerRecord<K, V> createRecord(String topic, Integer partition, Long timestamp,
                                                     K key, V value, Iterable<Header> headers) {
        validateTopic(topic);
        validatePartition(partition);
        validateTimestamp(timestamp);
        if (headers == null) {
            return new ProducerRecord<>(topic, partition, timestamp, key, value);
        }
        return new ProducerRecord<>(topic, partition, timestamp, key, value, headers);
    }

    private void validateRouteRecord(KafkaRouteRecord<?, ?> record) {
        if (record == null) {
            throw invalidRecord("record 不能为空");
        }
        validateTopic(record.getTopic());
        validatePartition(record.getPartition());
        validateTimestamp(record.getTimestamp());
        if (record.getRouteKey() != null && !KafkaRouteStringHelper.hasText(record.getRouteKey())) {
            throw invalidRecord("routeKey 不能为空");
        }
    }

    private void validateProducerRecord(ProducerRecord<?, ?> record) {
        if (record == null) {
            throw invalidRecord("record 不能为空");
        }
        validateTopic(record.topic());
        validatePartition(record.partition());
        validateTimestamp(record.timestamp());
    }

    private void validateTopic(String topic) {
        if (!KafkaRouteStringHelper.hasText(topic)) {
            throw new RouteException(ErrorCode.KAFKA_ROUTE_008, ErrorMessage.ROUTE_INPUT_EMPTY);
        }
    }

    private void validateRouteKey(String routeKey) {
        if (!KafkaRouteStringHelper.hasText(routeKey)) {
            throw new RouteException(ErrorCode.KAFKA_ROUTE_008, ErrorMessage.ROUTE_INPUT_EMPTY);
        }
    }

    private void validatePartition(Integer partition) {
        if (partition != null && partition < 0) {
            throw invalidRecord("partition 不能小于 0");
        }
    }

    private void validateTimestamp(Long timestamp) {
        if (timestamp != null && timestamp < 0) {
            throw invalidRecord("timestamp 不能小于 0");
        }
    }

    private void validateCallback(Function<KafkaTemplate<Object, Object>, ?> callback) {
        if (callback == null) {
            throw new RouteException(ErrorCode.KAFKA_ROUTE_010, ErrorMessage.CALLBACK_EMPTY);
        }
    }

    private RouteException invalidRecord(String reason) {
        return new RouteException(ErrorCode.KAFKA_ROUTE_008, String.format(ErrorMessage.RECORD_INVALID, reason));
    }

    @SuppressWarnings("unchecked")
    private <K, V> ListenableFuture<SendResult<K, V>> doSend(String datasourceKey, ProducerRecord<K, V> record) {
        return doSend(registry.getKafkaTemplate(datasourceKey), record);
    }

    @SuppressWarnings("unchecked")
    private <K, V> ListenableFuture<SendResult<K, V>> doSend(KafkaTemplate<Object, Object> template,
                                                             ProducerRecord<K, V> record) {
        ProducerRecord<Object, Object> objectRecord = (ProducerRecord<Object, Object>) (ProducerRecord<?, ?>) record;
        return (ListenableFuture<SendResult<K, V>>) (ListenableFuture<?>) template.send(objectRecord);
    }
}
