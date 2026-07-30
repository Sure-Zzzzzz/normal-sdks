package io.github.surezzzzzz.sdk.messaging.kafka.consumer.model;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;

/**
 * 消费记录封装，在原始 {@link ConsumerRecord} 之上暴露解析后的 messageId、所属 datasource 与 ack 能力
 *
 * @param <K> key 类型
 * @param <V> value 类型
 * @author surezzzzzz
 */
@Getter
public class KafkaConsumerRecord<K, V> {

    private final String messageId;
    private final String datasourceKey;
    private final String registrationId;
    private final String topic;
    private final int partition;
    private final long offset;
    private final K key;
    private final V value;
    private final Headers headers;
    private final Acknowledgment acknowledgment;

    private KafkaConsumerRecord(String messageId, String datasourceKey, String registrationId,
                                ConsumerRecord<K, V> record, Acknowledgment acknowledgment) {
        this.messageId = messageId;
        this.datasourceKey = datasourceKey;
        this.registrationId = registrationId;
        this.topic = record.topic();
        this.partition = record.partition();
        this.offset = record.offset();
        this.key = record.key();
        this.value = record.value();
        this.headers = record.headers();
        this.acknowledgment = acknowledgment;
    }

    /**
     * 从原始 ConsumerRecord 构造
     *
     * @param record         原始消费记录
     * @param messageId      解析后的 messageId
     * @param datasourceKey  该消息所属 datasource
     * @param acknowledgment ack 句柄
     * @param <K>            key 类型
     * @param <V>            value 类型
     * @return 封装后的消费记录
     */
    public static <K, V> KafkaConsumerRecord<K, V> of(ConsumerRecord<K, V> record, String messageId,
                                                      String datasourceKey, Acknowledgment acknowledgment) {
        return of(record, messageId, datasourceKey, null, acknowledgment);
    }

    /**
     * 从原始 ConsumerRecord 构造，并关联消费注册项。
     *
     * @param record         原始消费记录
     * @param messageId      解析后的 messageId
     * @param datasourceKey  该消息所属 datasource
     * @param registrationId 消费注册项标识
     * @param acknowledgment ack 句柄
     * @param <K>            key 类型
     * @param <V>            value 类型
     * @return 封装后的消费记录
     */
    public static <K, V> KafkaConsumerRecord<K, V> of(ConsumerRecord<K, V> record, String messageId,
                                                      String datasourceKey, String registrationId,
                                                      Acknowledgment acknowledgment) {
        return new KafkaConsumerRecord<>(messageId, datasourceKey, registrationId, record, acknowledgment);
    }

    /**
     * 提交 offset，仅当 ack 句柄存在时生效
     */
    public void acknowledge() {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    /**
     * 读取指定 header 的字符串值
     *
     * @param name header 名
     * @return header 值，不存在返回 null
     */
    public String header(String name) {
        if (headers == null) {
            return null;
        }
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * 构建消费事件上下文，用于事件回调
     *
     * @param eventType    事件类型
     * @param attempt      尝试次数
     * @param errorCode    错误码，无则传 null
     * @param errorSummary 错误摘要（已脱敏），无则传 null
     * @return 事件上下文
     */
    public KafkaConsumerEventContext toEventContext(ConsumerEventType eventType, int attempt,
                                                    String errorCode, String errorSummary) {
        return KafkaConsumerEventContext.builder()
                .eventType(eventType)
                .messageId(messageId)
                .topic(topic)
                .partition(partition)
                .offset(offset)
                .attempt(attempt)
                .errorCode(errorCode)
                .errorSummary(errorSummary)
                .datasourceKey(datasourceKey)
                .registrationId(registrationId)
                .build();
    }
}
