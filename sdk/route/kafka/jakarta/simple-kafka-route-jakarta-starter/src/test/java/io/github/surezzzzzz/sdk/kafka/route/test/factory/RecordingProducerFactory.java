package io.github.surezzzzzz.sdk.kafka.route.test.factory;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ProducerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用可记录 ProducerFactory
 *
 * @author surezzzzzz
 */
public class RecordingProducerFactory implements ProducerFactory<Object, Object>, DisposableBean {

    private final String datasourceKey;
    private final List<MockProducer<Object, Object>> producers = new ArrayList<>();
    private int createProducerCount;
    private boolean destroyed;

    public RecordingProducerFactory(String datasourceKey) {
        this.datasourceKey = datasourceKey;
    }

    @Override
    public Producer<Object, Object> createProducer() {
        createProducerCount++;
        @SuppressWarnings("unchecked")
        Serializer<Object> serializer = (Serializer<Object>) (Serializer<?>) new StringSerializer();
        MockProducer<Object, Object> producer = new MockProducer<>(true, serializer, serializer);
        producers.add(producer);
        return producer;
    }

    @Override
    public Producer<Object, Object> createProducer(String txIdPrefix) {
        return createProducer();
    }

    @Override
    public void destroy() {
        this.destroyed = true;
    }

    public String getDatasourceKey() {
        return datasourceKey;
    }

    public List<ProducerRecord<Object, Object>> getRecords() {
        List<ProducerRecord<Object, Object>> records = new ArrayList<>();
        for (MockProducer<Object, Object> producer : producers) {
            records.addAll(producer.history());
        }
        return records;
    }

    public int getCreateProducerCount() {
        return createProducerCount;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
