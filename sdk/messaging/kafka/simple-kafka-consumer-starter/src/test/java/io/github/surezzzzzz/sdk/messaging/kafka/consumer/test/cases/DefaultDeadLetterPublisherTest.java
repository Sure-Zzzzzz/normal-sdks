package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DefaultDeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 默认死信投递器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultDeadLetterPublisherTest {

    @Test
    public void testDisabledDeadLetterDoesNotAccessRegistry() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getError().getDeadLetter().setEnable(false);

        boolean published = new DefaultDeadLetterPublisher(registry, properties)
                .publish(record(), new IllegalArgumentException("mock failure"), 1, "MOCK_001");
        log.info("关闭 DLT 的处理结果：published={}", published);

        assertTrue(published);
        verify(registry, never()).containsDatasource(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void testPublishPreservesRecordAndAppendsTraceHeaders() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(registry.containsDatasource("mock-datasource")).thenReturn(true);
        when(registry.getKafkaTemplate("mock-datasource")).thenReturn(template);
        SettableListenableFuture<Object> future = new SettableListenableFuture<>();
        future.set(new Object());
        when(template.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);

        boolean published = new DefaultDeadLetterPublisher(registry, new SimpleKafkaConsumerProperties())
                .publish(record(), new IllegalArgumentException("mock failure"), 3, "MOCK_001");
        log.info("DLT 投递处理结果：published={}", published);

        assertTrue(published);
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<?, ?> actual = captor.getValue();
        log.info("死信记录：topic={}，key={}，value={}", actual.topic(), actual.key(), actual.value());

        assertEquals("mock-topic.DLT", actual.topic());
        assertEquals("mock-key", actual.key());
        assertEquals("mock-value", actual.value());
        assertEquals("mock-topic", header(actual, SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_TOPIC));
        assertEquals("2", header(actual, SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_PARTITION));
        assertEquals("7", header(actual, SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_OFFSET));
        assertEquals("MOCK_001", header(actual, SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ERROR_CODE));
        assertEquals("mock failure", header(actual, SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ERROR_SUMMARY));
        assertEquals("3", header(actual, SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ATTEMPT));
        assertEquals("original", header(actual, "mock-header"));
    }

    @Test
    public void testExplicitDeadLetterDatasourceOverridesSourceAndKeepsDuplicateHeaders() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(registry.containsDatasource("mock-dlt-datasource")).thenReturn(true);
        when(registry.getKafkaTemplate("mock-dlt-datasource")).thenReturn(template);
        SettableListenableFuture<Object> future = new SettableListenableFuture<>();
        future.set(new Object());
        when(template.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getError().getDeadLetter().setDatasourceKey(" mock-dlt-datasource ");
        KafkaConsumerRecord<String, String> source = record();
        source.getHeaders().add("mock-header", "duplicate".getBytes(StandardCharsets.UTF_8));

        boolean published = new DefaultDeadLetterPublisher(registry, properties)
                .publish(source, new IllegalArgumentException("mock failure"), 1, "MOCK_001");
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(registry).containsDatasource("mock-dlt-datasource");
        verify(template).send(captor.capture());
        int duplicateHeaderCount = headerCount(captor.getValue(), "mock-header");
        log.info("指定 DLT 数据源处理结果：published={}，duplicateHeaderCount={}", published, duplicateHeaderCount);

        assertTrue(published);
        assertEquals(2, duplicateHeaderCount);
    }

    @Test
    public void testExecutionFailureReturnsFalse() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(registry.containsDatasource("mock-datasource")).thenReturn(true);
        when(registry.getKafkaTemplate("mock-datasource")).thenReturn(template);
        SettableListenableFuture<Object> future = new SettableListenableFuture<>();
        future.setException(new IllegalStateException("mock send failure"));
        when(template.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);

        boolean published = new DefaultDeadLetterPublisher(registry, new SimpleKafkaConsumerProperties())
                .publish(record(), new IllegalArgumentException("mock failure"), 1, "MOCK_001");
        log.info("DLT 执行失败结果：published={}", published);

        assertFalse(published, "Kafka future 执行失败时不得把 DLT 当作成功");
    }

    @Test
    public void testTimeoutReturnsFalse() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(registry.containsDatasource("mock-datasource")).thenReturn(true);
        when(registry.getKafkaTemplate("mock-datasource")).thenReturn(template);
        ListenableFuture<Object> future = mock(ListenableFuture.class);
        try {
            when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException("mock timeout"));
        } catch (Exception e) {
            throw new IllegalStateException("配置 DLT 超时 Future 失败", e);
        }
        when(template.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);

        boolean published = new DefaultDeadLetterPublisher(registry, new SimpleKafkaConsumerProperties())
                .publish(record(), new IllegalArgumentException("mock failure"), 1, "MOCK_001");
        log.info("DLT 超时结果：published={}", published);

        assertFalse(published, "Kafka future 超时时不得把 DLT 当作成功");
    }

    @Test
    public void testInterruptedSendRestoresInterruptStatusAndReturnsFalse() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(registry.containsDatasource("mock-datasource")).thenReturn(true);
        when(registry.getKafkaTemplate("mock-datasource")).thenReturn(template);
        SettableListenableFuture<Object> future = new SettableListenableFuture<>();
        future.setException(new InterruptedException("mock interruption"));
        when(template.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);

        try {
            boolean published = new DefaultDeadLetterPublisher(registry, new SimpleKafkaConsumerProperties())
                    .publish(record(), new IllegalArgumentException("mock failure"), 1, "MOCK_001");
            log.info("DLT 中断结果：published={}，interrupted={}", published, Thread.currentThread().isInterrupted());

            assertFalse(published, "Kafka future 中断时不得把 DLT 当作成功");
            assertTrue(Thread.currentThread().isInterrupted(), "DLT 等待中断后必须恢复线程中断标志");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testMissingDatasourceFailsWithoutSend() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        when(registry.containsDatasource("mock-datasource")).thenReturn(false);

        boolean published = new DefaultDeadLetterPublisher(registry, new SimpleKafkaConsumerProperties())
                .publish(record(), new IllegalArgumentException("mock failure"), 1, "MOCK_001");
        log.info("缺少 DLT 数据源处理结果：published={}", published);

        assertFalse(published);
        verify(registry, never()).getKafkaTemplate(org.mockito.ArgumentMatchers.anyString());
    }

    private KafkaConsumerRecord<String, String> record() {
        ConsumerRecord<String, String> source = new ConsumerRecord<>("mock-topic", 2, 7L, "mock-key", "mock-value");
        source.headers().add("mock-header", "original".getBytes(StandardCharsets.UTF_8));
        return KafkaConsumerRecord.of(source, "mock-message", "mock-datasource", null);
    }

    private int headerCount(ProducerRecord<?, ?> record, String name) {
        int count = 0;
        for (org.apache.kafka.common.header.Header header : record.headers().headers(name)) {
            count++;
        }
        return count;
    }

    private String header(ProducerRecord<?, ?> record, String name) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
