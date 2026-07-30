package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 默认死信投递器：使用 route-starter 的 KafkaTemplate 同步投递，不依赖 publisher-starter。
 * 死信 topic = 原 topic + 配置后缀；原始 payload、key 和 header 透传，附带溯源与错误 header。
 * 投递结果由调用方处理；投递失败时调用方不得确认原消息，以保留后续重投机会。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultDeadLetterPublisher implements DeadLetterPublisher {

    private final SimpleKafkaRouteRegistry registry;
    private final SimpleKafkaConsumerProperties properties;

    public DefaultDeadLetterPublisher(SimpleKafkaRouteRegistry registry, SimpleKafkaConsumerProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public boolean publish(KafkaConsumerRecord<?, ?> record, Exception cause, int attempt, String errorCode) {
        if (!properties.getError().getDeadLetter().isEnable()) {
            log.debug("死信投递未启用，跳过：topic=[{}]", record.getTopic());
            return true;
        }
        String datasourceKey = resolveDatasourceKey(record);
        if (!KafkaConsumerStringHelper.hasText(datasourceKey) || !registry.containsDatasource(datasourceKey)) {
            log.warn("死信投递 datasource 不存在或为空：datasource=[{}]，topic=[{}]", datasourceKey, record.getTopic());
            return false;
        }
        String deadLetterTopic = record.getTopic() + properties.getError().getDeadLetter().getSuffix();
        ProducerRecord<Object, Object> producerRecord = buildRecord(record, deadLetterTopic, attempt, errorCode, cause);
        try {
            KafkaTemplate<Object, Object> template = registry.getKafkaTemplate(datasourceKey);
            template.send(producerRecord).get(
                    SimpleKafkaConsumerConstant.DEFAULT_DEAD_LETTER_SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.info("死信投递成功：origTopic=[{}]，deadLetterTopic=[{}]，messageId=[{}]，attempt=[{}]",
                    record.getTopic(), deadLetterTopic, KafkaConsumerStringHelper.safeDisplay(record.getMessageId()),
                    attempt);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("死信投递被中断：origTopic=[{}]，deadLetterTopic=[{}]，messageId=[{}]",
                    record.getTopic(), deadLetterTopic, KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
            return false;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("死信投递失败：origTopic=[{}]，deadLetterTopic=[{}]，messageId=[{}]",
                    record.getTopic(), deadLetterTopic, KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
            return false;
        } catch (Exception e) {
            log.warn("死信投递失败：origTopic=[{}]，deadLetterTopic=[{}]，messageId=[{}]",
                    record.getTopic(), deadLetterTopic, KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
            return false;
        }
    }

    private String resolveDatasourceKey(KafkaConsumerRecord<?, ?> record) {
        String configured = KafkaConsumerStringHelper.trimToNull(
                properties.getError().getDeadLetter().getDatasourceKey());
        if (configured != null) {
            return configured;
        }
        return record.getDatasourceKey();
    }

    private ProducerRecord<Object, Object> buildRecord(KafkaConsumerRecord<?, ?> record, String deadLetterTopic,
                                                       int attempt, String errorCode, Exception cause) {
        List<Header> headers = new ArrayList<>();
        if (record.getHeaders() != null) {
            for (Header header : record.getHeaders()) {
                headers.add(new RecordHeader(header.key(), header.value()));
            }
        }
        headers.add(new RecordHeader(SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_TOPIC,
                bytes(record.getTopic())));
        headers.add(new RecordHeader(SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_PARTITION,
                bytes(String.valueOf(record.getPartition()))));
        headers.add(new RecordHeader(SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_OFFSET,
                bytes(String.valueOf(record.getOffset()))));
        headers.add(new RecordHeader(SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ERROR_CODE,
                bytes(errorCode)));
        headers.add(new RecordHeader(SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ERROR_SUMMARY,
                bytes(KafkaConsumerStringHelper.safeForErrorMessage(cause == null ? null : cause.getMessage()))));
        headers.add(new RecordHeader(SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ATTEMPT,
                bytes(String.valueOf(attempt))));
        return new ProducerRecord<>(deadLetterTopic, null, null, record.getKey(), record.getValue(), headers);
    }

    private byte[] bytes(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }
}
